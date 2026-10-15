# DPIP Phase 1 Requirements

This document contains a consolidated list of the detailed specifications and constraints for Phase 1 of the Developer Project Intelligence Platform (DPIP).

---

## 1. Project & Vision Module
*   **Req 1.1**: Create a Project with fields: `id` (UUID), `name`, `description`, `category`, `difficulty` (Beginner, Intermediate, Advanced, Expert), `status` (Idea, Planning, In Progress, On Hold, Completed, Abandoned), `startDate`, `targetCompletionDate`, `createdAt`, `updatedAt`.
*   **Req 1.2**: Project status transitions must follow logical flows (e.g. Cannot complete a project without progressive development).
*   **Req 1.3**: Every project contains exactly one Vision containing `id`, `projectId`, `visionStatement`, `createdAt`, `updatedAt`.
*   **Req 1.4**: Deleting a project cascades and deletes the associated Vision.

## 2. Epic & Feature Module
*   **Req 2.1**: Create Epics inside a Project. Fields: `id`, `projectId`, `name`, `description`, `priority` (Low, Medium, High, Critical), `status` (Planned, In Progress, Completed).
*   **Req 2.2**: Create Features inside Epics. Fields: `id`, `epicId`, `name`, `description`, `priority`, `status` (Backlog, Planned, In Progress, Testing, Completed).
*   **Req 2.3**: **Feature Dependencies**: Features can depend on other features (many-to-many relationship using source and target UIDs).
*   **Req 2.4**: **Blocked Feature Check**: Prevent moving a feature to `In Progress` if its dependent features are not `Completed`.

## 3. Requirement & Technology Research Module
*   **Req 3.1**: Create Requirements inside Features. Requirements must remain tech-independent. Fields: `id`, `featureId`, `title`, `description`, `complexity` (Low, Medium, High, Very High), `priority`, `estimatedEffort`, `notes`.
*   **Req 3.2**: Attach optional User Stories to Requirements using the format: `As a [User] I want [Capability] So that [Benefit]`.
*   **Req 3.3**: **Technology Candidates**: Map candidate technologies to Requirements for research.
*   **Req 3.4**: **Selected Technologies & Decision Tracking**: Record the selection reason, advantages, tradeoffs, and comments.

## 4. Task & Kanban System
*   **Req 4.1**: Create Tasks inside Features. Fields: `id`, `featureId`, `title`, `description`, `priority`, `estimatedHours`, `actualHours`, `status` (Backlog, Planning, In Progress, Testing, Completed).
*   **Req 4.2**: Implement a Kanban board service to transition tasks through columns: `Backlog`, `Planning`, `In Progress`, `Testing`, `Completed`.
*   **Req 4.3**: **Audit Logging**: Every transition of a task must write a log record containing: `taskId`, `previousStatus`, `newStatus`, `timestamp`.

## 5. Progress Tracking Engine
*   *Progress is automatically calculated and never manually entered by the user:*
*   **Req 5.1**: **Task Level**: Calculate task progress based on completion state.
*   **Req 5.2**: **Feature Level**: Completed Requirements and Tasks / Total Requirements and Tasks.
*   **Req 5.3**: **Epic Level**: Average Feature Completion.
*   **Req 5.4**: **Project Level**: Average Epic Completion.

## 6. Search System
*   **Req 6.1**: Query all entities (Projects, Epics, Features, Requirements, Tasks, Technologies) using keyword matching.
*   **Req 6.2**: Support multi-select filters on: `Status`, `Priority`, `Category`, `Technology`, and `Completion State`.
