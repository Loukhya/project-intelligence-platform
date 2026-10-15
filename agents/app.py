import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv

# Load local environment variables (if any)
load_dotenv()

# Import our CrewAI runner methods
import sys
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from crew_runner import decompose_requirement, run_bdd_agent, run_tdd_agent

app = FastAPI(title="DPIP CrewAI Agent Runner Service")

class DecomposeRequest(BaseModel):
    taskId: str
    requirementId: str
    requirementTitle: str
    requirementDescription: str
    userStory: str = ""
    acceptanceCriteria: str = ""

@app.get("/health")
def health_check():
    return {"status": "UP", "message": "Agent service is healthy"}

@app.post("/api/generate")
def api_decompose(payload: DecomposeRequest):
    try:
        context_data = {
            "requirement_title": payload.requirementTitle,
            "requirement_description": payload.requirementDescription,
            "user_story": payload.userStory,
            "acceptance_criteria": payload.acceptanceCriteria
        }
        res = decompose_requirement(payload.taskId, payload.requirementId, context_data)
        return {"status": "SUCCESS", "data": res}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Decomposition failed: {str(e)}")

@app.post("/api/bdd/{subtask_id}")
def api_run_bdd(subtask_id: str):
    try:
        res = run_bdd_agent(subtask_id)
        return {"status": "SUCCESS", "data": res}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"BDD generation failed: {str(e)}")

@app.post("/api/tdd/{subtask_id}")
def api_run_tdd(subtask_id: str):
    try:
        res = run_tdd_agent(subtask_id)
        return {"status": "SUCCESS", "data": res}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"TDD generation failed: {str(e)}")
