Feature: Project & Vision Management
  As a developer
  I want to create and manage projects with a clear vision statement
  So that I can maintain traceability from the initial project idea.

  Scenario: Create a project successfully with valid attributes
    Given a new project payload with name "Growth OS", category "Learner", and difficulty "Beginner"
    When the project is created
    Then the project should be saved with status "Idea"
    And a unique UUID should be generated
    And the created and updated timestamps should be populated

  Scenario: Every project must have exactly one Vision statement
    Given a project "Growth OS" exists
    When a vision statement "A system to track learning progress" is added to the project
    Then the vision should be saved and linked to the project
    And retrieving the project's vision should return "A system to track learning progress"

  Scenario: Project deletion cascades to delete its associated Vision
    Given a project "Growth OS" exists with a vision "A system to track learning progress"
    When the project is deleted
    Then the project should no longer exist in the database
    And the associated vision should be deleted from the database
