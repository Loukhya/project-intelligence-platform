Feature: Search & Filter System
  As a developer
  I want to search across all entities and apply multi-select filters
  So that I can quickly find items by status, priority, category, and completion state.

  Scenario: Search across all entities by keyword
    Given several entities exist:
      | Type       | Name / Title          | Description / Notes    |
      | Project    | Platform Development  | Cloud based platform   |
      | Feature    | Database Layer        | Map database entities  |
      | Task       | Design schema         | Database layout design |
      | Technology | PostgreSQL            | Relational database    |
    When searching for keyword "database"
    Then the search results should contain the Feature, Task, and Technology

  Scenario: Apply multi-select priority and status filters
    Given several Tasks exist:
      | Title             | Priority | Status      |
      | Create User Entity| High     | In Progress |
      | Implement JWT     | High     | Backlog     |
      | Write migrations  | Low      | In Progress |
    When searching with filters: priority "High", status "In Progress"
    Then the results should only contain "Create User Entity"
