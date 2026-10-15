# ===================================================================
# DPIP - Supabase Environment Variable Setup Template (PowerShell)
# Copy this file to set-supabase-env.ps1 and supply your actual credentials.
# (set-supabase-env.ps1 is ignored by git to protect your secrets)
# ===================================================================

# --- Supabase JDBC Connection URL ---
[System.Environment]::SetEnvironmentVariable(
    "SUPABASE_DB_URL",
    "jdbc:postgresql://db.<your-project-ref>.supabase.co:5432/postgres?sslmode=require",
    "User"
)

# --- Supabase Database Username ---
[System.Environment]::SetEnvironmentVariable(
    "SUPABASE_DB_USERNAME",
    "postgres",
    "User"
)

# --- Supabase Database Password ---
[System.Environment]::SetEnvironmentVariable(
    "SUPABASE_DB_PASSWORD",
    "your_supabase_password_here",
    "User"
)

# --- Groq / OpenAI API Key for Agent ---
[System.Environment]::SetEnvironmentVariable(
    "GROQ_API_KEY",
    "your_api_key_here",
    "User"
)

Write-Host "Supabase environment template configured. Please update values and restart your shell." -ForegroundColor Green
