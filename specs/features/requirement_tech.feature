Feature: Requirement & Technology Research
  As a developer
  I want to create technology-independent requirements and evaluate technology candidates
  So that I can make and track informed architectural decisions.

  Scenario: Create Requirement successfully
    Given an existing Feature "User Login"
    When a Requirement "Support email login" is created for the Feature with complexity "Medium" and priority "High"
    Then the Requirement should be saved and linked to the Feature

  Scenario: Attach optional User Story to Requirement
    Given an existing Requirement "Support email login"
    When a User Story "As a user I want to login using email So that I can access my account" is attached to the Requirement
    Then the User Story should be saved and linked to the Requirement

  Scenario: Associate Technology Candidates to Requirement
    Given an existing Requirement "Support email login"
    And two Technologies exist: "Spring Security" and "Keycloak"
    When adding "Spring Security" and "Keycloak" as candidates to the Requirement
    Then both technologies should be listed as candidates for the Requirement

  Scenario: Select Technology and record Decision Tracking details
    Given an existing Requirement "Support email login"
    And a Technology "Spring Security" exists
    When selecting "Spring Security" for the Requirement with details: why "Standard choice", advantages "Stateless, secure", tradeoffs "Complex config"
    Then the decision should be saved and linked to the Requirement and Technology
    And retrieving the decision details should return why "Standard choice", advantages "Stateless, secure", tradeoffs "Complex config"
