Feature: Epic & Feature Management
  As a developer
  I want to create Epics and Features and declare dependencies between Features
  So that I can identify build paths and blocked work.

  Scenario: Create Epic successfully
    Given an existing project "Growth OS"
    When an Epic named "Authentication" is created for the project
    Then the Epic should be saved with status "Planned"
    And priority "High"

  Scenario: Create Feature successfully
    Given an existing Epic "Authentication"
    When a Feature named "User Login" is created for the Epic
    Then the Feature should be saved with status "Backlog"
    And priority "Medium"

  Scenario: Declare dependency between Features
    Given two existing Features: "Role Management" and "User Login"
    When "Role Management" is configured to depend on "User Login"
    Then the dependency should be successfully recorded in the graph

  Scenario: Blocked Feature Check - Dependent feature not completed
    Given Feature "Role Management" depends on Feature "User Login"
    And Feature "User Login" has status "In Progress"
    When attempting to transition Feature "Role Management" status to "In Progress"
    Then the transition should be blocked with an error "Feature is blocked by incomplete dependencies"

  Scenario: Blocked Feature Check - Dependent feature completed
    Given Feature "Role Management" depends on Feature "User Login"
    And Feature "User Login" has status "Completed"
    When attempting to transition Feature "Role Management" status to "In Progress"
    Then the transition should succeed and its status should become "In Progress"
