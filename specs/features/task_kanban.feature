Feature: Task & Kanban System
  As a developer
  I want to manage tasks inside features on a Kanban board and audit their state changes
  So that I can execute implementation work and maintain progress accountability.

  Scenario: Create Task successfully
    Given an existing Feature "User Login"
    When a Task "Create User Entity" is created for the Feature with priority "High" and estimated hours 4.0
    Then the Task should be saved with status "Backlog"
    And actual hours should be 0.0

  Scenario: Transition Task status and record Audit Log entry
    Given an existing Task "Create User Entity" with status "Backlog"
    When transitioning the Task status to "In Progress"
    Then the Task status should be updated to "In Progress"
    And a TaskAuditLog entry should be recorded with: previous "Backlog", new "In Progress", and a non-null timestamp
