import os
import json
import psycopg2
from psycopg2.extras import RealDictCursor

# Monkey-patch CrewAI to disable Anthropic-specific prompt caching parameter on Groq/OpenAI
try:
    import crewai.llms.cache as _crewai_cache
    _crewai_cache.mark_cache_breakpoint = lambda message: message
except ImportError:
    pass

from crewai import Agent, Task, Crew, LLM

# Configure LLM
def get_llm():
    # Use Groq or OpenAI or fallback
    api_key = os.getenv("GROQ_API_KEY")
    if api_key:
        return LLM(model="groq/llama-3.3-70b-versatile", api_key=api_key)
    api_key = os.getenv("OPENAI_API_KEY")
    if api_key:
        return LLM(model="openai/gpt-4o-mini", api_key=api_key)
    raise ValueError("No LLM API Key (GROQ_API_KEY or OPENAI_API_KEY) found in environment.")

# Database Connection
def get_db_connection():
    db_url = os.getenv("SUPABASE_DB_URL")
    if db_url:
        if db_url.startswith("jdbc:"):
            db_url = db_url[5:]
        
        from urllib.parse import urlparse, parse_qsl, urlencode, urlunparse
        u = urlparse(db_url)
        username = os.getenv("SUPABASE_DB_USERNAME", "postgres")
        password = os.getenv("SUPABASE_DB_PASSWORD")
        
        netloc = u.netloc
        if not u.username and username:
            if password:
                netloc = f"{username}:{password}@{netloc}"
            else:
                netloc = f"{username}@{netloc}"
                
        q = parse_qsl(u.query)
        supported_keys = {'sslmode', 'sslcert', 'sslkey', 'sslrootcert', 'sslcrl', 'application_name', 'connect_timeout'}
        filtered_q = [(k, v) for k, v in q if k.lower() in supported_keys]
        new_query = urlencode(filtered_q)
        db_url = urlunparse((u.scheme, netloc, u.path, u.params, new_query, u.fragment))
        return psycopg2.connect(db_url)
    
    # Fallback to credentials
    username = os.getenv("SUPABASE_DB_USERNAME", "postgres")
    password = os.getenv("SUPABASE_DB_PASSWORD")
    host = os.getenv("SUPABASE_DB_HOST", "localhost")
    port = "5432"
    database = "postgres"
    
    return psycopg2.connect(
        dbname=database,
        user=username,
        password=password,
        host=host,
        port=port
    )

# Helper to resolve project package context
def get_project_context(task_id):
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT p.name, p.local_path "
                "FROM public.projects p "
                "JOIN public.epics e ON e.project_id = p.id "
                "JOIN public.features f ON f.epic_id = e.id "
                "JOIN public.tasks t ON t.feature_id = f.id "
                "WHERE t.id = %s;",
                (task_id,)
            )
            res = cur.fetchone()
            if res:
                project_name, local_path = res[0], res[1]
                folder_name = os.path.basename(os.path.normpath(local_path))
                if folder_name == 'string-utility':
                    pkg = 'com.stringutil'
                elif folder_name == 'math-utility':
                    pkg = 'com.mathutil'
                elif folder_name == 'spring':
                    pkg = 'com.dpi.platform'
                else:
                    pkg = 'com.example'
                    java_dir = os.path.join(local_path, 'src', 'main', 'java')
                    if os.path.isdir(java_dir):
                        for root, dirs, files in os.walk(java_dir):
                            if any(f.endswith('.java') for f in files):
                                rel_path = os.path.relpath(root, java_dir)
                                if rel_path != '.':
                                    pkg = rel_path.replace(os.sep, '.')
                                    break
                return {
                    "project_name": project_name,
                    "local_path": local_path,
                    "package": pkg
                }
            return {
                "project_name": "Unknown Project",
                "local_path": "",
                "package": "com.example"
            }
    finally:
        conn.close()

# 1. Primary Coordinator - Decompose Requirement
def decompose_requirement(task_id, requirement_id, context_data):
    llm = get_llm()
    
    primary_agent = Agent(
        role="Requirement Analyst and Task Decomposer",
        goal="Break down project requirements and acceptance criteria into a concrete pair of subtasks: one for BDD Test Specification and one for TDD Implementation.",
        backstory="You are a principal system architect. You analyze features and design clean interfaces, mapping out exactly what tests must be written to cover acceptance criteria and what code structures should implement them.",
        verbose=True,
        allow_delegation=False,
        llm=llm
    )
    
    decomposition_task = Task(
        description=(
            f"Analyze the following requirement context:\n"
            f"Requirement: {context_data.get('requirement_title')}\n"
            f"Description: {context_data.get('requirement_description')}\n"
            f"User Story: {context_data.get('user_story')}\n"
            f"Acceptance Criteria: {context_data.get('acceptance_criteria')}\n\n"
            f"Decompose this requirement into two clear parts:\n"
            f"1. A test specification describing what behavior needs to be tested in JUnit 5.\n"
            f"2. An implementation specification describing what Java classes, fields, or methods need to be created.\n\n"
            f"Return your response as a valid JSON object matching this exact format:\n"
            f"{{\n"
            f"  \"test_spec\": \"Detailed instructions for writing the test cases...\",\n"
            f"  \"impl_spec\": \"Detailed instructions for writing the implementation code...\"\n"
            f"}}\n"
            f"Return ONLY valid JSON. No conversational prefix, no markdown block wrappers."
        ),
        expected_output="A JSON object with test_spec and impl_spec keys.",
        agent=primary_agent
    )
    
    crew = Crew(agents=[primary_agent], tasks=[decomposition_task], verbose=True)
    result = crew.kickoff()
    
    # Parse results
    result_text = str(result).strip()
    if result_text.startswith("```"):
        first_newline = result_text.find("\n")
        last_backticks = result_text.rfind("```")
        if first_newline != -1 and last_backticks != -1:
            result_text = result_text[first_newline + 1:last_backticks].strip()
            
    parsed = json.loads(result_text)
    
    # Insert subtasks in queue
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            # 1. Insert Test Generation Subtask
            cur.execute(
                "INSERT INTO public.agent_subtask_queue (task_id, requirement_id, subtask_type, status, error_log) "
                "VALUES (%s, %s, 'TEST_GENERATION', 'PENDING', %s) RETURNING id;",
                (task_id, requirement_id, parsed.get("test_spec"))
            )
            row_test = cur.fetchone()
            if row_test is None:
                raise ValueError("Failed to insert test subtask")
            test_subtask_id = row_test[0]
            
            # 2. Insert Code Implementation Subtask
            cur.execute(
                "INSERT INTO public.agent_subtask_queue (task_id, requirement_id, subtask_type, status, error_log) "
                "VALUES (%s, %s, 'CODE_IMPLEMENTATION', 'PENDING', %s) RETURNING id;",
                (task_id, requirement_id, parsed.get("impl_spec"))
            )
            row_impl = cur.fetchone()
            if row_impl is None:
                raise ValueError("Failed to insert implementation subtask")
            impl_subtask_id = row_impl[0]
            
            conn.commit()
            return {"test_subtask_id": str(test_subtask_id), "impl_subtask_id": str(impl_subtask_id)}
    finally:
        conn.close()

# 2. BDD Agent - Generate Test Code
def run_bdd_agent(subtask_id):
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                "SELECT q.id, q.task_id, q.requirement_id, q.error_log as test_spec, "
                "r.title as req_title, r.description as req_desc, r.acceptance_criteria "
                "FROM public.agent_subtask_queue q "
                "JOIN public.requirements r ON q.requirement_id = r.id "
                "WHERE q.id = %s;",
                (subtask_id,)
            )
            subtask = cur.fetchone()
            if not subtask:
                return {"error": "Subtask not found"}
            
            # Mark as claimed
            cur.execute("UPDATE public.agent_subtask_queue SET status = 'CLAIMED', updated_at = now() WHERE id = %s;", (subtask_id,))
            conn.commit()
    finally:
        conn.close()
        
    llm = get_llm()
    
    bdd_agent = Agent(
        role="Behavior-Driven Test Engineer",
        goal="Write comprehensive JUnit 5 unit tests that assert all behavior described in the test specification and requirement.",
        backstory="You are an expert QA automation engineer specializing in JUnit 5 and TDD. You write clean, compiling Java unit tests with appropriate mock mappings using Mockito if needed.",
        verbose=True,
        allow_delegation=False,
        llm=llm
    )
    
    context = get_project_context(subtask['task_id'])
    pkg_name = context['package']

    test_task = Task(
        description=(
            f"Requirement: {subtask['req_title']}\n"
            f"Description: {subtask['req_desc']}\n"
            f"Acceptance Criteria: {subtask['acceptance_criteria']}\n\n"
            f"Test Specification: {subtask['test_spec']}\n\n"
            f"Write a complete, valid JUnit 5 test class. Ensure to include package '{pkg_name}', imports, class header, and multiple tests.\n"
            f"Return your response as a valid JSON object matching this exact format:\n"
            f"{{\n"
            f"  \"testFilePath\": \"src/test/java/{pkg_name.replace('.', '/')}/[ClassName]Test.java\",\n"
            f"  \"testProposedContent\": \"...complete JUnit 5 Java code...\"\n"
            f"}}\n"
            f"Return ONLY valid JSON. No markdown backticks."
        ),
        expected_output="A JSON object with testFilePath and testProposedContent.",
        agent=bdd_agent
    )
    
    crew = Crew(agents=[bdd_agent], tasks=[test_task], verbose=True)
    result = crew.kickoff()
    
    result_text = str(result).strip()
    if result_text.startswith("```"):
        first_newline = result_text.find("\n")
        last_backticks = result_text.rfind("```")
        if first_newline != -1 and last_backticks != -1:
            result_text = result_text[first_newline + 1:last_backticks].strip()
            
    parsed = json.loads(result_text)
    
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE public.agent_subtask_queue "
                "SET test_code = %s, status = 'SUCCESS', error_log = %s, updated_at = now() "
                "WHERE id = %s;",
                (parsed.get("testProposedContent"), parsed.get("testFilePath"), subtask_id)
            )
            conn.commit()
            return {"status": "SUCCESS", "testFilePath": parsed.get("testFilePath")}
    finally:
        conn.close()

# 3. TDD Agent - Generate Code Implementation
def run_tdd_agent(subtask_id):
    conn = get_db_connection()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            # Fetch the TDD subtask
            cur.execute(
                "SELECT q.id, q.task_id, q.requirement_id, q.error_log as impl_spec, "
                "r.title as req_title, r.description as req_desc, r.acceptance_criteria "
                "FROM public.agent_subtask_queue q "
                "JOIN public.requirements r ON q.requirement_id = r.id "
                "WHERE q.id = %s;",
                (subtask_id,)
            )
            subtask = cur.fetchone()
            if not subtask:
                return {"error": "Subtask not found"}
            
            # Fetch the companion BDD subtask test code
            cur.execute(
                "SELECT test_code, error_log as test_file_path "
                "FROM public.agent_subtask_queue "
                "WHERE task_id = %s AND subtask_type = 'TEST_GENERATION' AND status = 'SUCCESS';",
                (subtask["task_id"],)
            )
            test_info = cur.fetchone()
            if not test_info or not test_info.get("test_code"):
                return {"error": "Corresponding test suite not written yet"}
            
            # Mark as claimed
            cur.execute("UPDATE public.agent_subtask_queue SET status = 'CLAIMED', updated_at = now() WHERE id = %s;", (subtask_id,))
            conn.commit()
    finally:
        conn.close()
        
    llm = get_llm()
    
    tdd_agent = Agent(
        role="Senior Java Software Developer",
        goal="Write clean, compiling production Java classes that satisfy the BDD JUnit test cases.",
        backstory="You are an elite Java 21 software engineer. You read test suites and write optimal, bug-free implementation code to make all assertions pass.",
        verbose=True,
        allow_delegation=False,
        llm=llm
    )
    
    context = get_project_context(subtask['task_id'])
    pkg_name = context['package']

    impl_task = Task(
        description=(
            f"Requirement: {subtask['req_title']}\n"
            f"Description: {subtask['req_desc']}\n\n"
            f"Implementation Instructions: {subtask['impl_spec']}\n\n"
            f"BDD Unit Test Code to satisfy:\n"
            f"```java\n{test_info['test_code']}\n```\n\n"
            f"Write the complete Java class file. Add package '{pkg_name}', imports, and logic.\n"
            f"Return your response as a valid JSON object matching this exact format:\n"
            f"{{\n"
            f"  \"filePath\": \"src/main/java/{pkg_name.replace('.', '/')}/[ClassName].java\",\n"
            f"  \"proposedContent\": \"...complete Java source code...\"\n"
            f"}}\n"
            f"Return ONLY valid JSON."
        ),
        expected_output="A JSON object with filePath and proposedContent.",
        agent=tdd_agent
    )
    
    crew = Crew(agents=[tdd_agent], tasks=[impl_task], verbose=True)
    result = crew.kickoff()
    
    result_text = str(result).strip()
    if result_text.startswith("```"):
        first_newline = result_text.find("\n")
        last_backticks = result_text.rfind("```")
        if first_newline != -1 and last_backticks != -1:
            result_text = result_text[first_newline + 1:last_backticks].strip()
            
    parsed = json.loads(result_text)
    
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE public.agent_subtask_queue "
                "SET proposed_code = %s, test_code = %s, status = 'SUCCESS', error_log = %s, updated_at = now() "
                "WHERE id = %s;",
                (parsed.get("proposedContent"), test_info["test_code"], parsed.get("filePath"), subtask_id)
            )
            conn.commit()
            return {"status": "SUCCESS", "filePath": parsed.get("filePath")}
    finally:
        conn.close()
