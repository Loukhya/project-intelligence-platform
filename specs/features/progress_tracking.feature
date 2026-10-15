Feature: Hierarchical Progress Tracking Engine
  As a developer
  I want progress to be automatically calculated from task and requirement completions
  So that I can see the real-time completion state of my features, epics, and projects.

  Scenario: Automatic progress propagation on Task completion
    Given a Project with an Epic and a Feature
    And the Feature has two Tasks
    When one Task is transitioned to status "Completed"
    Then the Feature progress should be updated to 50.0%
    And the Epic progress should be updated to 50.0%
    And the Project progress should be updated to 50.0%

  Scenario: Automatic progress propagation on Requirement research completion
    Given a Project with an Epic and a Feature
    And the Feature has one Task and one Requirement
    And the Task is status "Completed" (making progress 50.0%)
    When a Technology Decision is selected for the Requirement
    Then the Requirement should be considered completed
    And the Feature progress should be updated to 100.0%
    And the Epic progress should be updated to 100.0%
    And the Project progress should be updated to 100.0%
