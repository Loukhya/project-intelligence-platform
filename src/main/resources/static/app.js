// app.js - UI Controller for DPIP Platform Single Page Application

let state = {
    projects: [],
    technologies: [],
    activeProjectId: null,
    activeFeatureId: null,
    activeExplorerProject: null,
    activeExplorerEpics: [],
    activeExplorerFeatures: [],
    kanbanProjectFeatures: []
};

// Global Initialization
document.addEventListener("DOMContentLoaded", () => {
    switchTab('projects');
    loadTechnologies();
    loadProjects();
    initWorkspaceSplitter();
    fetch('/api/config').then(r => r.json()).then(config => window.appConfig = config).catch(() => {});
});

// Routing
function switchTab(tabId) {
    document.querySelectorAll(".screen-panel").forEach(p => p.classList.remove("active"));
    document.querySelectorAll(".nav-item").forEach(b => b.classList.remove("active"));
    
    document.getElementById(`panel-${tabId}`).classList.add("active");
    document.getElementById(`btn-${tabId}`).classList.add("active");

    if (tabId === 'projects') {
        loadProjects().then(() => {
            populateProjectSelectors();
            if (state.activeProjectId) {
                document.getElementById("project-select").value = state.activeProjectId;
            }
        });
    } else if (tabId === 'kanban') {
        populateKanbanProjectSelectors().then(() => {
            if (state.activeProjectId) {
                document.getElementById("kanban-project-select").value = state.activeProjectId;
                loadKanbanData(state.activeProjectId);
            } else {
                loadKanbanData(null);
            }
        });
    } else if (tabId === 'technologies') {
        loadTechnologies();
    } else if (tabId === 'graph') {
        populateGraphProjectSelectors().then(() => {
            if (state.activeProjectId) {
                document.getElementById("graph-project-select").value = state.activeProjectId;
                loadGraphData(state.activeProjectId);
            } else {
                loadGraphData(null);
            }
        });
    } else if (tabId === 'search') {
        triggerGlobalSearch();
    } else if (tabId === 'intelligence') {
        populateIntelligenceProjectSelectors();
        if (state.activeProjectId) {
            loadGraphData(state.activeProjectId);
        } else {
            loadGraphData(null);
        }
    }
}

async function apiCall(endpoint, method = 'GET', body = null) {
    const config = {
        method,
        headers: { 'Content-Type': 'application/json' }
    };
    if (body) {
        config.body = JSON.stringify(body);
    }
    try {
        const response = await fetch(endpoint, config);
        if (!response.ok) {
            const errorMsg = await response.text();
            throw new Error(errorMsg || `HTTP error ${response.status}`);
        }
        if (response.status === 204 || response.headers.get("content-length") === "0") {
            return null;
        }
        return await response.json();
    } catch (err) {
        alert(`API Error: ${err.message}`);
        throw err;
    }
}

// -------------------------------------------------------------
// PROJECT / DASHBOARD
// -------------------------------------------------------------
async function loadProjects() {
    const data = await apiCall('/api/projects'); // Fetch only projects
    state.projects = data || [];
    renderProjectsList();
}

function renderProjectsList() {
    const list = document.getElementById("projects-list");
    list.innerHTML = "";
    if (state.projects.length === 0) {
        list.innerHTML = `<div class="empty-state"><p>No projects found. Create one to get started!</p></div>`;
        return;
    }
    state.projects.forEach(p => {
        const card = document.createElement("div");
        card.className = "glass-card";
        card.innerHTML = `
            <div class="card-header-row">
                <h3>${escapeHtml(p.name)}</h3>
                <span class="badge badge-medium">${escapeHtml(p.status)}</span>
            </div>
            <p class="muted-text" style="font-size:13px; margin-bottom:12px;">${escapeHtml(p.description || 'No description')}</p>
            <div class="progress-bar-container">
                <div class="progress-track">
                    <div class="progress-fill" style="width: ${p.progress || 0}%"></div>
                </div>
                <span class="progress-txt">${p.progress || 0}% Complete</span>
            </div>
            <div class="project-metadata">
                <div><strong>Category:</strong> ${escapeHtml(p.category)}</div>
                <div><strong>Difficulty:</strong> ${escapeHtml(p.difficulty)}</div>
            </div>
            <button class="btn btn-secondary" style="margin-top:16px; width:100%;" onclick="exploreProject('${p.id}')">Explore Project</button>
        `;
        list.appendChild(card);
    });
}

function exploreProject(projectId) {
    switchTab('projects');
    document.getElementById("project-select").value = projectId;
    loadProjectDetails(projectId);
}

async function handleCreateProject(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const body = {
            name: document.getElementById("p-name").value,
            description: document.getElementById("p-desc").value,
            category: document.getElementById("p-category").value,
            difficulty: document.getElementById("p-difficulty").value,
            startDate: document.getElementById("p-start").value || null,
            targetCompletionDate: document.getElementById("p-target").value || null
        };
        const createdProject = await apiCall('/api/projects', 'POST', body);
        
        // Set vision statement if present
        const visionTxt = document.getElementById("p-vision").value;
        if (visionTxt.trim() && createdProject) {
            await apiCall(`/api/projects/${createdProject.id}/vision`, 'POST', { visionStatement: visionTxt });
        }
        
        closeModal('modal-create-project');
        loadProjects();
    } catch (err) {
        console.error("Create project failed", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function triggerSeedSampleProject() {
    const btn = document.getElementById("btn-seed-project");
    if (btn) btn.disabled = true;

    try {
        const seededProject = await apiCall('/api/projects/seed', 'POST');
        alert(`Sample project "${seededProject.name}" seeded successfully!`);
        
        await loadProjects();
        exploreProject(seededProject.id);
    } catch (err) {
        console.error("Failed to seed sample project", err);
    } finally {
        if (btn) btn.disabled = false;
    }
}

// SEARCH & FILTER
// -------------------------------------------------------------
function toggleFilterDrawer() {
    document.getElementById("filter-drawer").classList.toggle("active");
}

async function handleSearch() {
    const query = document.getElementById("search-input").value;
    
    // Extract checked filters
    const statuses = [];
    document.querySelectorAll("#filter-status-row input:checked").forEach(cb => statuses.push(cb.value));
    
    const priorities = [];
    document.querySelectorAll("#filter-priority-row input:checked").forEach(cb => priorities.push(cb.value));
    
    const completionState = document.getElementById("filter-completion").value;

    const resultsSection = document.getElementById("search-results-section");
    const gridSection = document.getElementById("projects-grid-section");
    const container = document.getElementById("search-results-container");

    if (!query.trim() && statuses.length === 0 && priorities.length === 0 && !completionState) {
        resultsSection.style.display = "none";
        gridSection.style.display = "block";
        return;
    }

    resultsSection.style.display = "block";
    gridSection.style.display = "none";
    container.innerHTML = "<p class='muted-text'>Loading results...</p>";

    // Build URLSearchParams
    const params = new URLSearchParams();
    if (query.trim()) params.append("query", query);
    statuses.forEach(s => params.append("status", s));
    priorities.forEach(p => params.append("priority", p));
    if (completionState) params.append("completionState", completionState);

    const results = await apiCall(`/api/search?${params.toString()}`);
    container.innerHTML = "";

    let hasResults = false;

    // Show matches
    if (results.projects && results.projects.length > 0) {
        hasResults = true;
        results.projects.forEach(p => {
            container.appendChild(createResultCard("Project", p.name, p.description, `Progress: ${p.progress}%`, () => exploreProject(p.id)));
        });
    }
    if (results.epics && results.epics.length > 0) {
        hasResults = true;
        results.epics.forEach(e => {
            container.appendChild(createResultCard("Epic", e.name, e.description, `Priority: ${e.priority}`, () => exploreProject(e.projectId)));
        });
    }
    if (results.features && results.features.length > 0) {
        hasResults = true;
        results.features.forEach(f => {
            container.appendChild(createResultCard("Feature", f.name, f.description, `Priority: ${f.priority} | Progress: ${f.progress}%`, () => exploreProject(f.projectId)));
        });
    }
    if (results.tasks && results.tasks.length > 0) {
        hasResults = true;
        results.tasks.forEach(t => {
            container.appendChild(createResultCard("Task", t.title, t.description, `Status: ${t.status} | Priority: ${t.priority}`, () => {
                switchTab('kanban');
                document.getElementById("kanban-project-select").value = t.projectId;
                loadKanbanData(t.projectId).then(() => {
                    filterKanbanBoardByFeature(t.featureId);
                });
            }));
        });
    }
    if (results.requirements && results.requirements.length > 0) {
        hasResults = true;
        results.requirements.forEach(r => {
            container.appendChild(createResultCard("Requirement", r.title, r.description, `Complexity: ${r.complexity}`, () => exploreProject(r.projectId)));
        });
    }
    if (results.technologies && results.technologies.length > 0) {
        hasResults = true;
        results.technologies.forEach(t => {
            container.appendChild(createResultCard("Technology", t.name, t.description, `Category: ${t.category}`, () => switchTab('technologies')));
        });
    }

    if (!hasResults) {
        container.innerHTML = "<p class='muted-text'>No matching entities found.</p>";
    }
}

function createResultCard(type, title, desc, meta, onClick) {
    const card = document.createElement("div");
    card.className = "glass-card result-card";
    card.style.cursor = "pointer";
    card.innerHTML = `
        <div class="card-header-row">
            <span class="badge badge-low">${type}</span>
            <span class="progress-txt" style="font-size:11px;">${escapeHtml(meta)}</span>
        </div>
        <h4 style="margin: 8px 0; font-size:15px;">${escapeHtml(title)}</h4>
        <p class="muted-text" style="font-size:12px;">${escapeHtml(desc || 'No description')}</p>
    `;
    card.onclick = onClick;
    return card;
}

// -------------------------------------------------------------
// PROJECT EXPLORER
// -------------------------------------------------------------
function populateProjectSelectors() {
    const select = document.getElementById("project-select");
    select.innerHTML = '<option value="">Select a Project...</option>';
    state.projects.forEach(p => {
        select.innerHTML += `<option value="${p.id}">${escapeHtml(p.name)}</option>`;
    });
    if (state.activeProjectId) {
        select.value = state.activeProjectId;
    }
}

async function loadProjectDetails(projectId) {
    if (!projectId) {
        state.activeProjectId = null;
        document.getElementById("project-explorer-content").classList.add("hidden-element");
        document.getElementById("project-explorer-empty").style.display = "block";
        document.getElementById("btn-add-epic").style.display = "none";
        document.getElementById("btn-add-feature").style.display = "none";
        document.getElementById("btn-scan-codebase").style.display = "none";
        return;
    }
    state.activeProjectId = projectId;
    document.getElementById("project-explorer-content").classList.remove("hidden-element");
    document.getElementById("project-explorer-content").style.display = "grid";
    document.getElementById("project-explorer-empty").style.display = "none";
    document.getElementById("btn-add-epic").style.display = "inline-block";
    document.getElementById("btn-add-feature").style.display = "inline-block";
    document.getElementById("btn-scan-codebase").style.display = "inline-block";
    
    // Default to planning sub-tab on project load
    switchProjectSubTab('planning');

    const fullProject = await apiCall(`/api/projects/${projectId}/full`);
    state.currentProjectFull = fullProject;
    state.activeExplorerProject = fullProject;
    state.activeExplorerEpics = fullProject.epics || [];
    
    // Flatten features
    state.activeExplorerFeatures = [];
    state.activeExplorerEpics.forEach(epic => {
        if (epic.features) {
            epic.features.forEach(feat => {
                // Ensure epicId is present
                feat.epicId = epic.id;
                state.activeExplorerFeatures.push(feat);
            });
        }
    });

    renderProjectExplorerSummary();
    renderEpicsAndFeatures();

    // Fetch and render codebase stats
    apiCall(`/api/projects/${projectId}/mappings`).then(mappings => {
        renderCodebaseStats(mappings);
    }).catch(err => {
        console.warn("Failed to load codebase stats", err);
    });
}

function renderProjectExplorerSummary() {
    const p = state.activeExplorerProject;
    document.getElementById("explorer-project-name").innerText = p.name;
    document.getElementById("explorer-project-desc").innerText = p.description || "No description";
    document.getElementById("explorer-project-progress").style.width = `${p.progress || 0}%`;
    document.getElementById("explorer-project-progress-txt").innerText = `${p.progress || 0}% Complete`;
    document.getElementById("explorer-project-difficulty").innerText = p.difficulty;
    document.getElementById("explorer-project-category").innerText = p.category;
    document.getElementById("explorer-project-status").innerText = p.status;
    document.getElementById("explorer-project-vision").innerText = p.vision ? p.vision.visionStatement : "No vision statement set. Click edit to set one.";
}

function openEditVisionModal() {
    const statement = state.activeExplorerProject.vision ? state.activeExplorerProject.vision.visionStatement : "";
    document.getElementById("v-statement").value = statement;
    openModal('modal-edit-vision');
}

async function handleUpdateVision(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const statement = document.getElementById("v-statement").value;
        await apiCall(`/api/projects/${state.activeProjectId}/vision`, 'POST', { visionStatement: statement });
        closeModal('modal-edit-vision');
        loadProjectDetails(state.activeProjectId);
    } catch (err) {
        console.error("Failed to update vision", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function handleCreateEpic(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const body = {
            projectId: state.activeProjectId,
            name: document.getElementById("e-name").value,
            description: document.getElementById("e-desc").value,
            priority: document.getElementById("e-priority").value
        };
        await apiCall('/api/epics', 'POST', body);
        closeModal('modal-create-epic');
        loadProjectDetails(state.activeProjectId);
        form.reset();
    } catch (err) {
        console.error("Failed to create epic", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// Populate Epic dropdown for Feature creation
function openModal(modalId) {
    document.getElementById(modalId).classList.add("active");
    if (modalId === 'modal-create-feature') {
        const epicSelect = document.getElementById("f-epic");
        epicSelect.innerHTML = "";
        state.activeExplorerEpics.forEach(e => {
            epicSelect.innerHTML += `<option value="${e.id}">${escapeHtml(e.name)}</option>`;
        });

        // Dependencies checklist
        const checklist = document.getElementById("dependency-checklist");
        checklist.innerHTML = "";
        state.activeExplorerFeatures.forEach(f => {
            checklist.innerHTML += `<label><input type="checkbox" value="${f.id}"> ${escapeHtml(f.name)}</label>`;
        });
    } else if (modalId === 'modal-create-task') {
        const featureSelect = document.getElementById("t-feature");
        featureSelect.innerHTML = '<option value="">Select Feature...</option>';
        
        const features = state.activeExplorerFeatures || [];
        features.forEach(f => {
            featureSelect.innerHTML += `<option value="${f.id}">${escapeHtml(f.name)}</option>`;
        });

        const reqSelect = document.getElementById("t-requirement");
        reqSelect.innerHTML = '<option value="">Select Requirement...</option>';

        if (state.activeFeatureId) {
            featureSelect.value = state.activeFeatureId;
            populateRequirementsForTaskFeature(state.activeFeatureId);
        }
    }
}

function populateRequirementsForTaskFeature(featureId) {
    const reqSelect = document.getElementById("t-requirement");
    reqSelect.innerHTML = '<option value="">Select Requirement...</option>';
    if (!featureId) return;

    const feature = state.activeExplorerFeatures.find(f => f.id === featureId);
    if (feature && feature.requirements) {
        feature.requirements.forEach(r => {
            reqSelect.innerHTML += `<option value="${r.id}">${escapeHtml(r.title)}</option>`;
        });
    }
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove("active");
}

async function handleCreateFeature(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const epicId = document.getElementById("f-epic").value;
        const body = {
            epicId,
            name: document.getElementById("f-name").value,
            description: document.getElementById("f-desc").value,
            priority: document.getElementById("f-priority").value
        };
        const createdFeature = await apiCall('/api/features', 'POST', body);
        
        // Save dependencies
        if (createdFeature) {
            const selectedDeps = [];
            document.querySelectorAll("#dependency-checklist input:checked").forEach(cb => selectedDeps.push(cb.value));
            if (selectedDeps.length > 0) {
                await apiCall(`/api/features/${createdFeature.id}/dependencies`, 'POST', selectedDeps);
            }
        }

        closeModal('modal-create-feature');
        loadProjectDetails(state.activeProjectId);
        form.reset();
    } catch (err) {
        console.error("Failed to create feature", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function renderEpicsAndFeatures() {
    const epicsList = document.getElementById("epics-list");
    epicsList.innerHTML = "";

    if (state.activeExplorerEpics.length === 0) {
        epicsList.innerHTML = `<p class="muted-text">No Epics defined. Add an epic to begin planning.</p>`;
        return;
    }

    state.activeExplorerEpics.forEach(epic => {
        const epicNode = createTreeNode('epic', epic.id, epic.name, `(${epic.progress || 0}% Complete)`, '📁', epic.status);
        epicsList.appendChild(epicNode.wrapper);

        const features = state.activeExplorerFeatures.filter(f => f.epicId === epic.id);
        
        if (features.length === 0) {
            epicNode.childrenContainer.innerHTML = `<p class="muted-text" style="font-size:12px; padding: 4px 12px;">No Features in this Epic yet.</p>`;
            return;
        }

        features.forEach(feature => {
            const featNode = createTreeNode('feature', feature.id, feature.name, `(${feature.progress || 0}% Complete)`, '⚡', feature.status);
            epicNode.childrenContainer.appendChild(featNode.wrapper);

            const reqs = feature.requirements || [];
            if (reqs.length === 0) {
                featNode.childrenContainer.innerHTML = `<p class="muted-text" style="font-size:11px; padding: 4px 12px;">No requirements mapped.</p>`;
            }

            reqs.forEach(req => {
                const reqNode = createTreeNode('requirement', req.id, req.title, '', '📌', req.status);
                featNode.childrenContainer.appendChild(reqNode.wrapper);

                // Clicking a requirement selects the feature and highlights the requirement card
                reqNode.header.onclick = (e) => {
                    e.stopPropagation();
                    
                    // Mark active requirement class in tree
                    document.querySelectorAll('.tree-node-header').forEach(h => h.classList.remove('active-requirement'));
                    reqNode.header.classList.add('active-requirement');
                    
                    selectExplorerFeature(feature.id, feature.name);
                    
                    // Highlight the requirement card on the right
                    setTimeout(() => {
                        const card = document.getElementById(`explorer-req-card-${req.id}`);
                        if (card) {
                            card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                            card.style.transition = 'box-shadow 0.3s, border-color 0.3s';
                            card.style.boxShadow = '0 0 15px rgba(139, 92, 246, 0.4)';
                            card.style.borderColor = 'var(--accent-indigo)';
                            setTimeout(() => {
                                card.style.boxShadow = '';
                                card.style.borderColor = '';
                            }, 2500);
                        }
                    }, 100);
                };

                // Find tasks for this requirement
                const tasks = feature.tasks || [];
                const reqTasks = tasks.filter(t => t.requirementId === req.id);

                reqTasks.forEach(task => {
                    const taskNode = createTreeNode('task', task.id, task.title, '', '⚙', task.status, true);
                    reqNode.childrenContainer.appendChild(taskNode.wrapper);
                });
            });
        });
    });

    // Reset side requirements panel state when rendering hierarchy tree
    document.getElementById("requirements-panel").style.display = "none";
    document.getElementById("requirements-panel-empty").style.display = "block";
}

function createTreeNode(type, id, title, subtitle = '', icon = '', status = '', isLeaf = false) {
    const wrapper = document.createElement("div");
    wrapper.className = `tree-node-wrapper tree-node-${type}`;
    wrapper.id = `tree-node-${type}-${id}`;

    const header = document.createElement("div");
    header.className = "tree-node-header";

    // Toggle icon (only if not a leaf node like tasks)
    let toggleHtml = '';
    if (!isLeaf) {
        toggleHtml = `<span class="tree-toggle-icon">▼</span>`;
    } else {
        toggleHtml = `<span class="tree-toggle-icon" style="opacity:0;"></span>`;
    }

    // Status badges
    let statusClass = 'badge-low';
    if (status) {
        const s = status.toUpperCase();
        if (s === 'SUCCESS' || s === 'COMPLETED' || s === 'DONE' || s === 'HIGH') {
            statusClass = 'badge-high';
        } else if (s === 'IN_PROGRESS' || s === 'TESTING' || s === 'MEDIUM') {
            statusClass = 'badge-medium';
        }
    }
    const badgeHtml = status ? `<span class="tree-node-badge badge ${statusClass}">${escapeHtml(status)}</span>` : '';

    header.innerHTML = `
        ${toggleHtml}
        <span class="tree-node-type-icon">${icon}</span>
        <span class="tree-node-title" title="${escapeHtml(title)}">${escapeHtml(title)} <span class="muted-text" style="font-size:11px;">${escapeHtml(subtitle)}</span></span>
        ${badgeHtml}
    `;

    wrapper.appendChild(header);

    let childrenContainer = null;
    if (!isLeaf) {
        childrenContainer = document.createElement("div");
        childrenContainer.className = "tree-node-children";
        wrapper.appendChild(childrenContainer);

        // Toggle click handler on arrow
        const toggleBtn = header.querySelector(".tree-toggle-icon");
        if (toggleBtn) {
            header.onclick = (e) => {
                // If it is feature, check if requirement clicked or just fold/unfold feature
                if (type === 'requirement') return; // clicked requirement header directly does navigation

                e.stopPropagation();
                const collapsed = childrenContainer.classList.toggle("collapsed-children");
                toggleBtn.classList.toggle("collapsed-icon", collapsed);
                
                if (type === 'feature') {
                    selectExplorerFeature(id, title);
                }
            };
        }
    } else {
        // leaf click handler (tasks)
        header.onclick = (e) => {
            e.stopPropagation();
            // Optional: navigate to workboard and highlight
            switchTab('kanban');
            // We can search for the task card on the board
            setTimeout(() => {
                const card = document.querySelector(`[data-task-id="${id}"]`);
                if (card) {
                    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    card.style.boxShadow = '0 0 15px rgba(20, 184, 166, 0.6)';
                    setTimeout(() => card.style.boxShadow = '', 2000);
                }
            }, 200);
        };
    }

    return { wrapper, header, childrenContainer };
}

function selectExplorerFeature(featureId, featureName) {
    state.activeFeatureId = featureId;
    
    document.querySelectorAll(".feature-tree-item").forEach(item => {
        item.classList.remove("active");
    });
    const activeItem = document.getElementById(`feature-tree-item-${featureId}`);
    if (activeItem) activeItem.classList.add("active");

    document.getElementById("requirements-panel").style.display = "block";
    document.getElementById("requirements-panel-empty").style.display = "none";
    
    document.getElementById("selected-feature-title").innerText = `Requirements: ${featureName}`;
    document.getElementById("q-req-feature-id").value = featureId;

    hideQuickAddReqForm();
    renderFeatureRequirementsPanel(featureId);
}

function renderFeatureRequirementsPanel(featureId) {
    const container = document.getElementById("requirements-list-container");
    container.innerHTML = "";

    const feature = state.activeExplorerFeatures.find(f => f.id === featureId);
    if (!feature) return;

    const reqs = feature.requirements || [];

    if (reqs.length === 0) {
        container.innerHTML = `<p class="muted-text" style="font-size: 13px; font-style: italic; padding: 20px 0;">No requirements mapped to this feature yet.</p>`;
        return;
    }

    reqs.forEach(req => {
        const reqCard = document.createElement("div");
        reqCard.id = `explorer-req-card-${req.id}`;
        reqCard.className = "requirement-card glass-card";
        reqCard.style.padding = "16px";
        reqCard.style.marginBottom = "16px";
        reqCard.style.borderLeft = "4px solid var(--accent-indigo)";

        const storyHtml = req.userStory ? `<div class="user-story-bubble" style="margin-top: 8px;">${escapeHtml(req.userStory.storyText)}</div>` : '';
        const acceptanceHtml = req.acceptanceCriteria ? `<div class="acceptance-criteria-box" style="margin-top: 10px;"><strong style="color: var(--accent-indigo);">Acceptance Criteria:</strong><div style="white-space: pre-wrap; margin-top: 4px;">${escapeHtml(req.acceptanceCriteria)}</div></div>` : '';

        // Decisions rendering
        let decisionsHtml = `<div id="explorer-decision-box-${req.id}"></div>`;

        reqCard.innerHTML = `
            <div class="requirement-header" style="display: flex; justify-content: space-between; align-items: flex-start; gap: 15px; flex-wrap: wrap;">
                <div>
                    <h4 style="font-size: 15px; font-weight: 600; color: var(--text-primary); margin: 0;">${escapeHtml(req.title)}</h4>
                    <div style="display: flex; gap: 8px; margin-top: 6px; align-items: center; flex-wrap: wrap;">
                        <span class="badge badge-medium" style="font-size: 10px;">Complexity: ${req.complexity}</span>
                        <span class="badge badge-low" style="font-size: 10px;">Priority: ${req.priority}</span>
                        <span class="badge badge-high" style="font-size: 10px;">Status: ${req.status}</span>
                        <span class="badge badge-low" style="font-size: 10px; background: rgba(16,185,129,0.1); color: var(--accent-emerald);">${req.linkedTaskCount || 0} Linked Tasks</span>
                    </div>
                </div>
                <div class="requirement-actions" style="display: flex; gap: 6px; flex-wrap: wrap;">
                    <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 11px;" onclick="openCandidatesModal('${req.id}')">Candidates</button>
                    <button class="btn btn-primary" style="padding: 4px 8px; font-size: 11px;" onclick="openSelectDecisionModal('${req.id}')">Select Decision</button>
                    <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 11px;" onclick="viewCodeArea('${req.id}', '${escapeHtml(req.title)}')">Code Area</button>
                    <button class="btn btn-secondary btn-emerald-dashed" style="padding: 4px 8px; font-size: 11px; font-weight: 600;" onclick="createTaskFromRequirementDirect('${req.id}', '${featureId}', '${escapeHtml(req.title)}')">Create Task →</button>
                </div>
            </div>
            ${req.description ? `<p class="requirement-desc" style="margin-top: 8px; font-size: 13px; color: var(--text-secondary); margin-bottom: 0;">${escapeHtml(req.description)}</p>` : ''}
            ${storyHtml}
            ${acceptanceHtml}
            ${decisionsHtml}
        `;

        container.appendChild(reqCard);

        // Load decisions
        apiCall(`/api/requirements/${req.id}/decisions`).then(decisions => {
            if (decisions && decisions.length > 0) {
                const box = document.getElementById(`explorer-decision-box-${req.id}`);
                if (box) {
                    decisions.forEach(dec => {
                        box.innerHTML += `
                            <div class="decision-bubble" style="margin-top: 8px; padding: 8px; background: rgba(255,255,255,0.02); border-radius: 6px; font-size: 12px;">
                                <strong>Selected:</strong> ${escapeHtml(dec.technology.name)}<br>
                                <strong>Why:</strong> ${escapeHtml(dec.whySelected)}
                            </div>
                        `;
                    });
                }
            }
        });
    });
}

async function createTaskFromRequirementDirect(requirementId, featureId, reqTitle) {
    const taskTitle = prompt("Enter title for the task to link with this requirement:", `Implement: ${reqTitle}`);
    if (!taskTitle || !taskTitle.trim()) return;

    const body = {
        featureId: featureId,
        title: taskTitle.trim(),
        description: `Task automatically created for requirement: ${reqTitle}`,
        priority: "MEDIUM",
        estimatedHours: 4.0,
        requirementId: requirementId
    };

    try {
        await apiCall('/api/tasks', 'POST', body);
        alert("Task created successfully pre-linked to this requirement! It is now visible on the Work Board (Kanban).");
        
        // Reload project data to fetch the new task count and updates
        if (state.activeProjectId) {
            await loadProjectDetails(state.activeProjectId);
            renderFeatureRequirementsPanel(featureId);
        }
    } catch (err) {
        console.error("Failed to create task", err);
    }
}

function showQuickAddReqForm() {
    document.getElementById("quick-add-req-container").classList.remove("hidden-element");
}

function hideQuickAddReqForm() {
    document.getElementById("quick-add-req-container").classList.add("hidden-element");
    document.getElementById("q-req-title").value = "";
    document.getElementById("q-req-desc").value = "";
    document.getElementById("q-req-story").value = "";
    document.getElementById("q-req-acceptance").value = "";
    document.getElementById("q-req-notes").value = "";
    document.getElementById("q-req-effort").value = "";
}

async function handleQuickCreateRequirement(e) {
    e.preventDefault();
    const featureId = document.getElementById("q-req-feature-id").value;
    const body = {
        featureId,
        title: document.getElementById("q-req-title").value,
        description: document.getElementById("q-req-desc").value,
        complexity: document.getElementById("q-req-complexity").value,
        priority: document.getElementById("q-req-priority").value,
        estimatedEffort: parseFloat(document.getElementById("q-req-effort").value) || null,
        notes: document.getElementById("q-req-notes").value,
        acceptanceCriteria: document.getElementById("q-req-acceptance").value
    };

    try {
        const req = await apiCall('/api/requirements', 'POST', body);

        // Save optional user story
        const storyText = document.getElementById("q-req-story").value;
        if (req && storyText.trim()) {
            await apiCall(`/api/requirements/${req.id}/user-story`, 'POST', { storyText });
        }

        hideQuickAddReqForm();
        loadProjectDetails(state.activeProjectId);
        renderFeatureRequirementsPanel(featureId);
    } catch (err) {
        console.error("Failed to create requirement", err);
    }
}

// Requirement Modal actions
function openCreateRequirementModal(featureId) {
    document.getElementById("req-feature-id").value = featureId;
    openModal('modal-create-req');
}

async function handleCreateRequirement(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const featureId = document.getElementById("req-feature-id").value;
        const body = {
            featureId,
            title: document.getElementById("req-title").value,
            description: document.getElementById("req-desc").value,
            complexity: document.getElementById("req-complexity").value,
            priority: document.getElementById("req-priority").value,
            estimatedEffort: parseFloat(document.getElementById("req-effort").value) || null,
            notes: document.getElementById("req-notes").value,
            acceptanceCriteria: document.getElementById("req-acceptance").value
        };
        const req = await apiCall('/api/requirements', 'POST', body);

        // Save optional user story
        const storyText = document.getElementById("req-story").value;
        if (req && storyText.trim()) {
            await apiCall(`/api/requirements/${req.id}/user-story`, 'POST', { storyText });
        }

        closeModal('modal-create-req');
        loadProjectDetails(state.activeProjectId);
        form.reset();
    } catch (err) {
        console.error("Failed to create requirement", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// Technology Candidates Checklist Selection
async function openCandidatesModal(reqId) {
    document.getElementById("cand-req-id").value = reqId;
    
    // Fetch currently selected candidates
    const selectedCands = await apiCall(`/api/requirements/${reqId}/candidates`);
    const selectedIds = (selectedCands || []).map(c => c.id);

    const checklist = document.getElementById("tech-candidates-checklist");
    checklist.innerHTML = "";
    state.technologies.forEach(tech => {
        const checked = selectedIds.includes(tech.id) ? "checked" : "";
        checklist.innerHTML += `<label><input type="checkbox" value="${tech.id}" ${checked}> ${escapeHtml(tech.name)}</label>`;
    });

    openModal('modal-tech-candidates');
}

async function handleSaveCandidates(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const reqId = document.getElementById("cand-req-id").value;
        const selectedIds = [];
        document.querySelectorAll("#tech-candidates-checklist input:checked").forEach(cb => selectedIds.push(cb.value));
        
        await apiCall(`/api/requirements/${reqId}/candidates`, 'POST', selectedIds);
        closeModal('modal-tech-candidates');
        loadProjectDetails(state.activeProjectId);
    } catch (err) {
        console.error("Failed to save candidates", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// Select technology decision
async function openSelectDecisionModal(reqId) {
    document.getElementById("dec-req-id").value = reqId;
    
    // Fetch candidates for selection options
    const candidates = await apiCall(`/api/requirements/${reqId}/candidates`);
    const select = document.getElementById("dec-tech-select");
    select.innerHTML = "";

    if (!candidates || candidates.length === 0) {
        select.innerHTML = '<option value="">(No candidates mapped, showing all)</option>';
        state.technologies.forEach(tech => {
            select.innerHTML += `<option value="${tech.id}">${escapeHtml(tech.name)}</option>`;
        });
    } else {
        candidates.forEach(tech => {
            select.innerHTML += `<option value="${tech.id}">${escapeHtml(tech.name)}</option>`;
        });
    }

    openModal('modal-select-decision');
}

async function handleSelectDecision(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const reqId = document.getElementById("dec-req-id").value;
        const body = {
            technologyId: document.getElementById("dec-tech-select").value,
            whySelected: document.getElementById("dec-why").value,
            advantages: document.getElementById("dec-advantages").value,
            tradeoffs: document.getElementById("dec-tradeoffs").value,
            notes: document.getElementById("dec-notes").value
        };

        await apiCall(`/api/requirements/${reqId}/decisions`, 'POST', body);
        closeModal('modal-select-decision');
        loadProjectDetails(state.activeProjectId);
        form.reset();
    } catch (err) {
        console.error('Failed to save technology decision', err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// -------------------------------------------------------------
// KANBAN BOARD
// -------------------------------------------------------------
async function populateKanbanProjectSelectors() {
    if (state.projects.length === 0) {
        state.projects = await apiCall('/api/projects') || [];
    }
    const select = document.getElementById('kanban-project-select');
    if (select) {
        select.innerHTML = '<option value="">Select Project...</option>';
        state.projects.forEach(p => {
            select.innerHTML += `<option value="${p.id}">${escapeHtml(p.name)}</option>`;
        });
        if (state.activeProjectId) {
            select.value = state.activeProjectId;
        }
    }
}

async function loadKanbanData(projectId) {
    const reviewBox = document.getElementById('review-content');
    if (reviewBox) reviewBox.classList.add('hidden-element');

    if (!projectId) {
        state.activeProjectId = null;
        document.getElementById('kanban-board-content').style.display = 'none';
        document.getElementById('kanban-board-content').classList.add('hidden-element');
        document.getElementById('btn-add-task').style.display = 'none';
        document.getElementById('btn-add-task').classList.add('hidden-element');
        document.getElementById('kanban-board-empty').style.display = 'flex';
        return;
    }
    state.activeProjectId = projectId;
    document.getElementById('kanban-board-content').style.display = 'flex';
    document.getElementById('kanban-board-content').classList.remove('hidden-element');
    document.getElementById('btn-add-task').style.display = 'block';
    document.getElementById('btn-add-task').classList.remove('hidden-element');
    document.getElementById('kanban-board-empty').style.display = 'none';

    const fullProject = await apiCall(`/api/projects/${projectId}/full`);
    state.currentProjectFull = fullProject;
    state.kanbanAllTasks = [];
    state.kanProjectFeatures = [];

    if (fullProject.epics) {
        fullProject.epics.forEach(epic => {
            if (epic.features) {
                epic.features.forEach(feat => {
                    feat.epicId = epic.id;
                    state.kanProjectFeatures.push(feat);
                    if (feat.tasks) {
                        feat.tasks.forEach(t => {
                            state.kanbanAllTasks.push(t);
                        });
                    }
                });
            }
        });
    }

    // Refresh activeExplorerFeatures to keep Create Task modal synced
    state.activeExplorerFeatures = state.kanProjectFeatures;

    // Render sidebar features overview list with task progress metrics
    const featuresListContainer = document.getElementById("kanban-features-list");
    featuresListContainer.innerHTML = "";
    
    const allActive = !state.kanbanActiveFeatureId ? "active" : "";
    featuresListContainer.innerHTML += `
        <div class="kanban-feature-item ${allActive}" id="feature-item-all" onclick="filterKanbanBoardByFeature(null)">
            <div class="kanban-feature-item-header">
                <span class="kanban-feature-item-title" style="max-width: 100%;">All Features</span>
                <span class="badge badge-low">${state.kanbanAllTasks.length} tasks</span>
            </div>
        </div>
    `;

    if (state.kanProjectFeatures.length === 0) {
        featuresListContainer.innerHTML += `<p class="muted-text" style="font-size:11px; padding:12px;">No features found.</p>`;
    } else {
        state.kanProjectFeatures.forEach(f => {
            const statusClass = getFeatureStatusBadgeClass(f.status);
            const activeClass = (state.kanbanActiveFeatureId === f.id) ? "active" : "";
            const total = f.taskCount || 0;
            const completed = f.completedTaskCount || 0;
            const pct = total > 0 ? Math.round((completed / total) * 100) : 0;
            
            featuresListContainer.innerHTML += `
                <div class="kanban-feature-item ${activeClass}" id="feature-item-${f.id}" onclick="filterKanbanBoardByFeature('${f.id}')">
                    <div class="kanban-feature-item-header">
                        <span class="kanban-feature-item-title" title="${escapeHtml(f.name)}">${escapeHtml(f.name)}</span>
                        <span class="kanban-feature-item-status badge ${statusClass}">${f.status}</span>
                    </div>
                    <div class="progress-bar-container" style="margin-top: 4px; gap: 2px;">
                        <div class="progress-track" style="height: 4px;">
                            <div class="progress-fill" style="width: ${pct}%"></div>
                        </div>
                        <span class="progress-txt" style="font-size: 9px; margin-top: 2px; text-align: left;">${completed}/${total} tasks (${pct}%)</span>
                    </div>
                </div>
            `;
        });
    }

    // Render tasks matching current filter
    const tasksToRender = state.kanbanActiveFeatureId 
        ? state.kanbanAllTasks.filter(t => t.featureId === state.kanbanActiveFeatureId)
        : state.kanbanAllTasks;
    renderKanbanTasks(tasksToRender);
}

function getFeatureStatusBadgeClass(status) {
    if (status === 'COMPLETED') return 'badge-low';
    if (status === 'TESTING') return 'badge-medium';
    if (status === 'IN_PROGRESS') return 'badge-high';
    return 'badge-critical';
}

function filterKanbanBoardByFeature(featureId) {
    state.kanbanActiveFeatureId = featureId;

    document.querySelectorAll(".kanban-feature-item").forEach(item => {
        item.classList.remove("active");
    });
    
    if (featureId) {
        const activeItem = document.getElementById(`feature-item-${featureId}`);
        if (activeItem) activeItem.classList.add("active");
    } else {
        const allItem = document.getElementById("feature-item-all");
        if (allItem) allItem.classList.add("active");
    }

    const tasksToRender = featureId 
        ? state.kanbanAllTasks.filter(t => t.featureId === featureId)
        : state.kanbanAllTasks;
    renderKanbanTasks(tasksToRender);
}

function renderKanbanTasks(tasks) {
    const cols = ['BACKLOG', 'PLANNING', 'IN_PROGRESS', 'TESTING', 'COMPLETED'];
    cols.forEach(col => {
        const container = document.getElementById(`list-${col}`);
        container.innerHTML = "";
        const colTasks = tasks.filter(t => t.status === col);
        document.getElementById(`badge-${col}`).innerText = colTasks.length;

        colTasks.forEach(task => {
            const card = document.createElement("div");
            card.className = "task-card";
            card.onclick = () => openTaskActionsModal(task);
            
            // Build action buttons inside card
            let actionBtn = "";
            if ((task.status === 'BACKLOG' || task.status === 'PLANNING') && task.requirementId) {
                actionBtn = `<button class="btn btn-secondary btn-ai-dashed" id="btn-ai-assign-${task.id}" style="margin-top:8px; font-size:11px; padding:6px; font-weight:600;" onclick="event.stopPropagation(); triggerAssignAiDirect('${task.id}')">🤖 Assign AI Agent</button>`;
            } else if ((task.status === 'BACKLOG' || task.status === 'PLANNING') && !task.requirementId) {
                actionBtn = `<button class="btn btn-secondary" style="margin-top:8px; font-size:11px; padding:6px; opacity:0.5; cursor:not-allowed;" disabled title="Link a requirement to assign AI" onclick="event.stopPropagation();">🤖 AI (No Req)</button>`;
            } else if (task.status === 'TESTING' && task.hasCodeCandidate) {
                actionBtn = `<button class="btn btn-secondary btn-emerald-dashed" style="margin-top:8px; font-size:11px; padding:6px; font-weight:600;" onclick="event.stopPropagation(); loadCandidateReviewInline('${task.id}')">🔍 Review AI Code</button>`;
            }

            const reqLabel = task.requirementTitle 
                ? `<div class="muted-text" style="font-size:11px; margin-top:6px; color:var(--accent-indigo); font-weight:500;">Req: ${escapeHtml(task.requirementTitle)}</div>`
                : '';

            const featLabel = `<div class="muted-text" style="font-size:10px; margin-top:2px;">Feature: ${escapeHtml(task.featureName)}</div>`;

            card.innerHTML = `
                <h5>${escapeHtml(task.title)}</h5>
                <p class="muted-text">${escapeHtml(task.description || 'No description')}</p>
                ${reqLabel}
                ${featLabel}
                <div class="task-card-footer" style="margin-top:8px;">
                    <span class="badge ${getPriorityBadgeClass(task.priority)}">${task.priority}</span>
                    <span class="hours-badge">${task.actualHours || 0} / ${task.estimatedHours || 0} hrs</span>
                </div>
                ${actionBtn}
            `;
            container.appendChild(card);
        });
    });
}

function getPriorityBadgeClass(priority) {
    if (priority === 'LOW') return 'badge-low';
    if (priority === 'MEDIUM') return 'badge-medium';
    if (priority === 'HIGH') return 'badge-high';
    return 'badge-critical';
}

async function handleCreateTask(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const featureId = document.getElementById("t-feature").value;
        const reqId = document.getElementById("t-requirement").value;
        const body = {
            featureId: featureId,
            title: document.getElementById("t-title").value,
            description: document.getElementById("t-desc").value,
            priority: document.getElementById("t-priority").value,
            estimatedHours: parseFloat(document.getElementById("t-estimated").value) || 0,
            requirementId: reqId ? reqId : null
        };
        await apiCall('/api/tasks', 'POST', body);
        closeModal('modal-create-task');
        
        // Reload project-wide Kanban board
        loadKanbanData(state.activeProjectId);
        form.reset();
    } catch (err) {
        console.error("Failed to create task", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// Task Transition Actions and Audit Logs
async function openTaskActionsModal(task) {
    document.getElementById("task-action-title").innerText = `Manage Task: ${task.title}`;
    document.getElementById("action-task-id").value = task.id;
    document.getElementById("action-status").value = task.status;
    document.getElementById("action-hours").value = task.actualHours || 0;

    // Load Audit Logs
    const auditLogs = await apiCall(`/api/tasks/${task.id}/audit`);
    const logsContainer = document.getElementById("task-audit-logs");
    logsContainer.innerHTML = "";

    if (!auditLogs || auditLogs.length === 0) {
        logsContainer.innerHTML = `<p class="muted-text" style="font-size:12px;">No state changes logged.</p>`;
    } else {
        auditLogs.forEach(log => {
            const time = new Date(log.timestamp).toLocaleString();
            logsContainer.innerHTML += `
                <div class="audit-log-item">
                    <strong>Transition:</strong> ${log.previousStatus || 'INIT'} ➔ ${log.newStatus}<br>
                    <small class="muted-text">${time}</small>
                </div>
            `;
        });
    }

    openModal('modal-task-actions');
}

async function handleTransitionTask(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const taskId = document.getElementById("action-task-id").value;
        const body = {
            newStatus: document.getElementById("action-status").value,
            actualHours: parseFloat(document.getElementById("action-hours").value) || 0
        };
        await apiCall(`/api/tasks/${taskId}/transition`, 'POST', body);
        closeModal('modal-task-actions');
        loadKanbanData(state.activeProjectId);
    } catch (err) {
        console.error("Failed to transition task", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// -------------------------------------------------------------
// TECHNOLOGY HUB
// -------------------------------------------------------------
async function loadTechnologies() {
    state.technologies = await apiCall('/api/technologies');
    renderTechnologiesList();
}

function renderTechnologiesList() {
    const list = document.getElementById("tech-list");
    list.innerHTML = "";
    if (state.technologies.length === 0) {
        list.innerHTML = `<div class="empty-state" style="grid-column: 1/-1;"><p>No technologies registered. Register tech to evaluate candidates.</p></div>`;
        return;
    }
    state.technologies.forEach(tech => {
        const card = document.createElement("div");
        card.className = "glass-card";
        card.innerHTML = `
            <div class="card-header-row">
                <h3>${escapeHtml(tech.name)}</h3>
                <span class="badge badge-medium">${escapeHtml(tech.category)}</span>
            </div>
            <p class="muted-text" style="font-size:13px; margin: 12px 0;">${escapeHtml(tech.description || 'No description')}</p>
            ${tech.officialDocumentation ? `<a href="${escapeHtml(tech.officialDocumentation)}" target="_blank" class="progress-txt" style="text-decoration:none; display:inline-block; font-size:12px;">Docs ➔</a>` : ''}
            ${tech.notes ? `<p class="muted-text" style="font-size:11px; margin-top:8px;"><em>Note: ${escapeHtml(tech.notes)}</em></p>` : ''}
        `;
        list.appendChild(card);
    });
}

async function handleCreateTech(e) {
    e.preventDefault();
    const form = e.target;
    const submitBtn = form.querySelector("button[type='submit']");
    if (submitBtn) submitBtn.disabled = true;

    try {
        const body = {
            name: document.getElementById("tech-name").value,
            category: document.getElementById("tech-category").value,
            description: document.getElementById("tech-desc").value,
            officialDocumentation: document.getElementById("tech-doc").value || null,
            notes: document.getElementById("tech-notes").value
        };
        await apiCall('/api/technologies', 'POST', body);
        closeModal('modal-create-tech');
        loadTechnologies();
        form.reset();
    } catch (err) {
        console.error("Failed to create technology", err);
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

// Helper to escape HTML characters
function escapeHtml(str) {
    if (!str) return '';
    return str.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// Codebase Scan and Code Area Graph Traversals
let currentViewReqId = null;
let currentViewReqTitle = "";

async function triggerProjectScan() {
    if (!state.activeProjectId) {
        alert("Please select a project first.");
        return;
    }
    const btn = document.querySelector(".header-actions button:last-child");
    const oldTxt = btn.innerText;
    btn.innerText = "Scanning...";
    btn.disabled = true;
    try {
        await apiCall(`/api/projects/${state.activeProjectId}/scan`, 'POST');
        alert("Codebase scanned successfully! Call graph and mappings updated.");
        loadProjectDetails(state.activeProjectId);
    } catch(e) {
        // Error alert is handled in apiCall helper
    } finally {
        btn.innerText = oldTxt;
        btn.disabled = false;
    }
}

function renderCodebaseStats(mappings) {
    mappings = mappings || [];
    const classes = new Set();
    const methods = new Set();
    const activeMappings = mappings.filter(m => m.mappingSource === 'TAG');

    mappings.forEach(m => {
        if (m.javaClass) classes.add(m.javaClass.id);
        if (m.javaMethod) methods.add(m.javaMethod.id);
    });

    document.getElementById("stats-classes-count").innerText = classes.size;
    document.getElementById("stats-methods-count").innerText = methods.size;
    document.getElementById("stats-mappings-count").innerText = activeMappings.length;

    const list = document.getElementById("stats-mappings-list");
    list.innerHTML = "";
    if (activeMappings.length === 0) {
        list.innerHTML = "<div class='muted-text' style='font-style:italic;'>No active code mappings. Use Javadoc tags (e.g. @Requirement) to link code!</div>";
        return;
    }
    activeMappings.forEach(m => {
        const item = document.createElement("div");
        item.style.padding = "4px";
        item.style.borderBottom = "1px solid rgba(255,255,255,0.03)";
        const target = m.javaMethod ? `Method: ${m.javaMethod.name}` : `Class: ${m.javaClass.name}`;
        item.innerHTML = `
            <div style="font-weight:600; color:var(--accent-indigo);">${escapeHtml(m.requirement.title)}</div>
            <div class="muted-text" style="font-size:10px;">${escapeHtml(target)} (${escapeHtml(m.javaClass.filePath)})</div>
        `;
        list.appendChild(item);
    });
}

function viewCodeArea(reqId, reqTitle) {
    currentViewReqId = reqId;
    currentViewReqTitle = reqTitle;
    document.getElementById("code-area-modal-title").innerText = `Code Area Context: ${reqTitle}`;
    openModal('modal-view-code-area');
    reloadCodeArea();
}

async function reloadCodeArea() {
    if (!currentViewReqId) return;
    const depth = document.getElementById("code-area-depth").value;
    const container = document.getElementById("code-area-list");
    container.innerHTML = "<p class='muted-text'>Traversing call graph...</p>";
    try {
        const mappings = await apiCall(`/api/requirements/${currentViewReqId}/code-area?maxDepth=${depth}`);
        renderCodeAreaList(mappings);
    } catch(e) {
        container.innerHTML = "<p class='muted-text'>Failed to fetch code area.</p>";
    }
}

function renderCodeAreaList(mappings) {
    const container = document.getElementById("code-area-list");
    container.innerHTML = "";
    if (!mappings || mappings.length === 0) {
        container.innerHTML = "<p class='muted-text' style='font-style:italic; padding: 12px;'>No code elements found in this area. Tag your classes or methods with @Requirement to map them.</p>";
        return;
    }

    // Sort by confidence score descending
    mappings.sort((a, b) => b.confidenceScore - a.confidenceScore);

    mappings.forEach(m => {
        const div = document.createElement("div");
        div.style.padding = "10px";
        div.style.border = "1px solid var(--border-color)";
        div.style.borderRadius = "6px";
        div.style.backgroundColor = "rgba(255,255,255,0.01)";

        const badgeClass = m.mappingSource === 'TAG' ? 'badge-high' : 'badge-low';
        const sourceLabel = m.mappingSource === 'TAG' ? 'Direct Tag' : 'Call Graph Dependency';
        const target = m.javaMethod ? `Method: <strong>${escapeHtml(m.javaMethod.name)}</strong>` : `Class: <strong>${escapeHtml(m.javaClass.name)}</strong>`;
        const path = m.javaClass.filePath;
        const confidencePct = Math.round(m.confidenceScore * 100);

        div.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                <span class="badge ${badgeClass}" style="font-size:10px;">${sourceLabel}</span>
                <span class="progress-txt" style="font-size:11px; font-weight:600; color:var(--accent-emerald);">${confidencePct}% relevance</span>
            </div>
            <div style="font-size:13px; margin:4px 0;">${target}</div>
            <div class="muted-text" style="font-size:11px;">Path: ${escapeHtml(path)}</div>
            ${m.javaMethod && m.javaMethod.parameters ? `<div class="muted-text" style="font-size:10px; font-family:monospace; margin-top:2px;">Args: (${escapeHtml(m.javaMethod.parameters)})</div>` : ''}
        `;
        container.appendChild(div);
    });
}

// -------------------------------------------------------------
// AI AGENT DELEGATION & CODE REVIEW
// -------------------------------------------------------------
function loadCandidateReviewInline(taskId) {
    const reviewContent = document.getElementById("review-content");
    reviewContent.classList.remove("hidden-element");
    reviewContent.scrollIntoView({ behavior: 'smooth' });
    loadCandidateReview(taskId);
}

function closeInlineReview() {
    document.getElementById("review-content").classList.add("hidden-element");
}

async function loadCandidateReview(taskId) {
    if (!taskId) {
        document.getElementById("review-content").classList.add("hidden-element");
        return;
    }

    // Set panel as active/visible immediately to show loading state
    document.getElementById("review-content").classList.remove("hidden-element");
    document.getElementById("review-content").dataset.taskId = taskId;

    // Reset contents to Loading... to prevent overlap of code between tasks
    document.getElementById("review-file-path").innerText = "Loading candidate details...";
    document.getElementById("diff-original").innerHTML = '<div class="diff-empty-file">Loading original class code...</div>';
    document.getElementById("diff-proposed").innerHTML = '<div class="diff-empty-file">Loading proposed code changes...</div>';
    document.getElementById("verification-log").innerText = "Loading compilation logs...";
    document.getElementById("test-execution-log").innerText = "Loading test logs...";

    // Reset badges
    renderTestSummaryBadges(null);

    // Refresh review queue sidebar navigation dynamically
    updatePendingReviewsQueue();

    try {
        const candidates = await apiCall(`/api/tasks/${taskId}/candidates`);
        const activeCandidate = candidates.find(c => c.status === 'PENDING');

        if (!activeCandidate) {
            document.getElementById("review-content").classList.add("hidden-element");
            alert("No pending AI code candidate found for this task.");
            return;
        }

        // Cache the active candidate ID on the view elements
        document.getElementById("review-content").dataset.candidateId = activeCandidate.id;

        // Render contents
        document.getElementById("review-file-path").innerText = `Source File: ${activeCandidate.filePath}`;
        const diffResult = computeLineDiff(activeCandidate.originalContent, activeCandidate.proposedContent);
        document.getElementById("diff-original").innerHTML = diffResult.originalHtml;
        document.getElementById("diff-proposed").innerHTML = diffResult.proposedHtml;
        document.getElementById("verification-log").innerText = activeCandidate.verificationLog || "No compilation logs yet. Click 'Verify Compilation' to run Maven check.";
        
        document.getElementById("test-execution-log").innerText = activeCandidate.testExecutionLog || "No test run logs yet. Click 'Run Unit Tests' to execute candidate test suite.";
        renderTestSummaryBadges(activeCandidate.testResultsSummary);
    } catch (err) {
        console.error("Failed to load candidate review", err);
        document.getElementById("review-content").classList.add("hidden-element");
    }
}

async function triggerAssignAiDirect(taskId) {
    const btnId = `btn-ai-assign-${taskId}`;
    const btn = document.getElementById(btnId);
    let originalText = "";
    if (btn) {
        originalText = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = "🤖 AI Working...";
        btn.style.opacity = "0.7";
    }

    try {
        const candidate = await apiCall(`/api/tasks/${taskId}/assign-ai`, 'POST');
        alert("Success! The AI developer agent has completed building the code candidate. We are displaying Code Review inline below.");
        
        // Reload project-wide Kanban board
        if (state.activeProjectId) {
            await loadKanbanData(state.activeProjectId);
        }
        
        // Show inline review panel
        loadCandidateReviewInline(taskId);
    } catch (err) {
        console.error("AI Assignment error", err);
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalText;
            btn.style.opacity = "1";
        }
    }
}

async function triggerAssignAi() {
    const taskId = document.getElementById("action-task-id").value;
    const btn = document.getElementById("btn-assign-ai");
    const originalText = btn.innerText;

    btn.disabled = true;
    btn.innerText = "🤖 AI Agent Working...";
    btn.style.opacity = "0.7";

    try {
        const candidate = await apiCall(`/api/tasks/${taskId}/assign-ai`, 'POST');
        alert("Success! The AI developer agent has completed building the code candidate. Displaying Code Review inline below.");
        
        closeModal('modal-task-actions');
        
        // Reload Kanban board
        if (state.activeProjectId) {
            await loadKanbanData(state.activeProjectId);
        }
        
        // Open review inline
        loadCandidateReviewInline(taskId);
    } catch (err) {
        console.error("AI Assignment error", err);
    } finally {
        btn.disabled = false;
        btn.innerText = originalText;
        btn.style.opacity = "1";
    }
}

async function verifyCandidateCode() {
    const candidateId = document.getElementById("review-content").dataset.candidateId;
    const logBox = document.getElementById("verification-log");
    
    logBox.innerText = "[DPIP Engine] Running 'mvn test-compile' on project codebase in background...\nThis usually takes about 10-15 seconds. Please wait...";
    setCandidateButtonsDisabled(true);
    
    try {
        const candidate = await apiCall(`/api/candidates/${candidateId}/verify`, 'POST');
        logBox.innerText = candidate.verificationLog;
    } catch (err) {
        logBox.innerText = `[DPIP Error] Failed to execute verification: ${err.message}`;
    } finally {
        setCandidateButtonsDisabled(false);
    }
}

async function approveCandidateCode() {
    const candidateId = document.getElementById("review-content").dataset.candidateId;
    const taskId = document.getElementById("review-content").dataset.taskId;

    if (!confirm("Are you sure you want to approve this AI code? This will write the changes physically to disk, execute Git Commit, and automatically rescan your codebase graph.")) {
        return;
    }

    setCandidateButtonsDisabled(true);

    try {
        await apiCall(`/api/candidates/${candidateId}/approve`, 'POST');
        alert("AI Candidate approved successfully! The code has been merged, committed to Git, and the codebase graph has been updated.");
        
        if (state.activeProjectId) {
            await loadKanbanData(state.activeProjectId);
        }
        
        // Auto-advance to the next pending review if there is one
        const pendingTasks = (state.kanbanAllTasks || []).filter(t => t.status === 'TESTING' && t.hasCodeCandidate);
        if (pendingTasks.length > 0) {
            loadCandidateReview(pendingTasks[0].id);
        } else {
            closeInlineReview();
        }
    } catch (err) {
        // Handled in helper
    } finally {
        setCandidateButtonsDisabled(false);
    }
}

async function rejectCandidateCode() {
    const candidateId = document.getElementById("review-content").dataset.candidateId;
    const taskId = document.getElementById("review-content").dataset.taskId;

    if (!confirm("Are you sure you want to reject this AI code candidate? This will revert the task status back to BACKLOG.")) {
        return;
    }

    setCandidateButtonsDisabled(true);

    try {
        await apiCall(`/api/candidates/${candidateId}/reject`, 'POST');
        alert("AI Candidate rejected. Task has been returned to backlog.");
        
        if (state.activeProjectId) {
            await loadKanbanData(state.activeProjectId);
        }
        
        // Auto-advance to the next pending review if there is one
        const pendingTasks = (state.kanbanAllTasks || []).filter(t => t.status === 'TESTING' && t.hasCodeCandidate);
        if (pendingTasks.length > 0) {
            loadCandidateReview(pendingTasks[0].id);
        } else {
            closeInlineReview();
        }
    } catch (err) {
        // Handled
    } finally {
        setCandidateButtonsDisabled(false);
    }
}

async function runCandidateTests() {
    const candidateId = document.getElementById("review-content").dataset.candidateId;
    const logBox = document.getElementById("test-execution-log");
    
    logBox.innerText = "[DPIP Engine] Spawning test generation & execution sandbox...\nRunning 'mvn test' in background to run candidate unit tests...\nThis usually takes about 10-15 seconds. Please wait...";
    setCandidateButtonsDisabled(true);
    
    try {
        const candidate = await apiCall(`/api/candidates/${candidateId}/run-tests`, 'POST');
        logBox.innerText = candidate.testExecutionLog;
        renderTestSummaryBadges(candidate.testResultsSummary);
    } catch (err) {
        logBox.innerText = `[DPIP Error] Failed to run tests: ${err.message}`;
    } finally {
        setCandidateButtonsDisabled(false);
    }
}

function renderTestSummaryBadges(summaryJson) {
    const badgeRun = document.getElementById("badge-tests-run");
    const badgePassed = document.getElementById("badge-tests-passed");
    const badgeFailed = document.getElementById("badge-tests-failed");

    if (!summaryJson) {
        badgeRun.innerText = "Run: 0";
        badgePassed.innerText = "Passed: 0";
        badgeFailed.innerText = "Failed: 0";
        return;
    }

    try {
        const summary = typeof summaryJson === 'string' ? JSON.parse(summaryJson) : summaryJson;
        
        badgeRun.innerText = `Run: ${summary.testsRun || 0}`;
        
        const failures = (summary.failures || 0);
        const errors = (summary.errors || 0);
        const failedCount = failures + errors;
        const runCount = (summary.testsRun || 0);
        const passedCount = runCount - failedCount;

        badgePassed.innerText = `Passed: ${passedCount >= 0 ? passedCount : 0}`;
        badgeFailed.innerText = `Failed: ${failedCount}`;
    } catch (e) {
        console.error("Failed to parse test summary JSON", e);
    }
}

function computeLineDiff(origStr, propStr) {
    if (!origStr) {
        const propLines = propStr.split('\n');
        return {
            originalHtml: '<div class="diff-empty-file">(New File - No pre-existing content on disk)</div>',
            proposedHtml: propLines.map((line, idx) => `<div class="diff-line diff-added"><span class="line-num">${idx+1}</span><span class="line-content">+ ${escapeHtml(line)}</span></div>`).join('')
        };
    }

    const origLines = origStr.split('\n');
    const propLines = propStr.split('\n');

    let i = 0, j = 0;
    let origHtml = '';
    let propHtml = '';
    let origLineNum = 1;
    let propLineNum = 1;

    while (i < origLines.length || j < propLines.length) {
        if (i < origLines.length && j < propLines.length && origLines[i] === propLines[j]) {
            origHtml += `<div class="diff-line"><span class="line-num">${origLineNum}</span><span class="line-content">  ${escapeHtml(origLines[i])}</span></div>`;
            propHtml += `<div class="diff-line"><span class="line-num">${propLineNum}</span><span class="line-content">  ${escapeHtml(propLines[j])}</span></div>`;
            i++;
            j++;
            origLineNum++;
            propLineNum++;
        } else {
            let foundMatch = false;
            let lookAheadLimit = 5;
            for (let k = 1; k <= lookAheadLimit; k++) {
                if (i + k < origLines.length && j < propLines.length && origLines[i + k] === propLines[j]) {
                    for (let m = 0; m < k; m++) {
                        origHtml += `<div class="diff-line diff-removed"><span class="line-num">${origLineNum}</span><span class="line-content">- ${escapeHtml(origLines[i])}</span></div>`;
                        propHtml += `<div class="diff-line diff-empty-placeholder">&nbsp;</div>`;
                        i++;
                        origLineNum++;
                    }
                    foundMatch = true;
                    break;
                }
                if (j + k < propLines.length && i < origLines.length && origLines[i] === propLines[j + k]) {
                    for (let m = 0; m < k; m++) {
                        origHtml += `<div class="diff-line diff-empty-placeholder">&nbsp;</div>`;
                        propHtml += `<div class="diff-line diff-added"><span class="line-num">${propLineNum}</span><span class="line-content">+ ${escapeHtml(propLines[j])}</span></div>`;
                        j++;
                        propLineNum++;
                    }
                    foundMatch = true;
                    break;
                }
            }
            if (!foundMatch) {
                if (i < origLines.length) {
                    origHtml += `<div class="diff-line diff-removed"><span class="line-num">${origLineNum}</span><span class="line-content">- ${escapeHtml(origLines[i])}</span></div>`;
                    i++;
                    origLineNum++;
                } else {
                    origHtml += `<div class="diff-line diff-empty-placeholder">&nbsp;</div>`;
                }
                if (j < propLines.length) {
                    propHtml += `<div class="diff-line diff-added"><span class="line-num">${propLineNum}</span><span class="line-content">+ ${escapeHtml(propLines[j])}</span></div>`;
                    j++;
                    propLineNum++;
                } else {
                    origHtml += `<div class="diff-line diff-empty-placeholder">&nbsp;</div>`;
                }
            }
        }
    }

    return { originalHtml: origHtml, proposedHtml: propHtml };
}

function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function setCandidateButtonsDisabled(disabled) {
    const reviewPanel = document.getElementById("review-content");
    if (!reviewPanel) return;
    const buttons = reviewPanel.querySelectorAll(".header-actions button");
    buttons.forEach(btn => {
        btn.disabled = disabled;
        if (disabled) {
            btn.style.opacity = "0.5";
            btn.style.pointerEvents = "none";
        } else {
            btn.style.opacity = "1";
            btn.style.pointerEvents = "auto";
        }
    });
}

function updatePendingReviewsQueue() {
    const queueContainer = document.getElementById("pending-reviews-queue");
    if (!queueContainer) return;

    const currentTaskId = document.getElementById("review-content").dataset.taskId;
    const pendingTasks = (state.kanbanAllTasks || []).filter(t => t.status === 'TESTING' && t.hasCodeCandidate);

    queueContainer.innerHTML = "";
    if (pendingTasks.length === 0) {
        queueContainer.innerHTML = '<p class="muted-text" style="font-size:11px; padding: 4px 0;">No active reviews in queue.</p>';
        return;
    }

    pendingTasks.forEach(task => {
        const isActive = (currentTaskId === task.id);
        const item = document.createElement("div");
        item.className = `queue-item ${isActive ? 'active-queue-item' : ''}`;
        item.style.padding = "10px";
        item.style.border = "1px solid var(--border-color)";
        item.style.borderRadius = "6px";
        item.style.marginBottom = "8px";
        item.style.cursor = "pointer";
        item.style.fontSize = "12px";
        item.onclick = () => {
            if (!isActive) {
                loadCandidateReview(task.id);
            }
        };
        
        item.innerHTML = `
            <div style="font-weight:600; color: ${isActive ? 'var(--text-primary)' : 'var(--text-secondary)'};">${escapeHtml(task.title)}</div>
            <div style="font-size:10px; color:var(--text-muted); margin-top:4px; text-overflow:ellipsis; overflow:hidden; white-space:nowrap;">Req: ${escapeHtml(task.requirementTitle || 'None')}</div>
        `;
        queueContainer.appendChild(item);
    });
}

// Graph Explorer and Change Impact Analyzer
let graphState = {
    nodes: [],
    links: [],
    visibleNodes: [],
    visibleLinks: [],
    transform: { x: 0, y: 0, k: 1 },
    dragNode: null,
    dragStart: null,
    dragOffset: { x: 0, y: 0 },
    selectedNode: null,
    hoveredNode: null,
    searchQuery: '',
    activeFilters: {
        EPIC: true,
        REQUIREMENT: true,
        TASK: true,
        CLASS: true,
        METHOD: true
    },
    // Force parameters
    friction: 0.85,
    repulsion: 350,
    springLength: 90,
    springCoeff: 0.05,
    gravity: 0.02,
    animFrameId: null,
    impactPaths: { upstream: new Set(), downstream: new Set() },
    codeAreaNodes: new Set(),
    aiHighlightedNodes: new Set(),
    collapsedNodes: new Set(),
    pulseTime: 0
};

let graphInitialized = false;

async function populateGraphProjectSelectors() {
    const selector = document.getElementById("graph-project-select");
    if (!selector) return;
    selector.innerHTML = '<option value="">-- Select Project --</option>';
    (state.projects || []).forEach(p => {
        selector.innerHTML += `<option value="${p.id}">${escapeHtml(p.name)}</option>`;
    });
}

async function loadGraphData(projectId) {
    initGraphCanvasListeners();
    cancelAnimationFrame(graphState.animFrameId);
    
    const panel = document.getElementById("graph-details-panel");
    if (panel) panel.classList.add("hidden-element");
    
    graphState.nodes = [];
    graphState.links = [];
    graphState.visibleNodes = [];
    graphState.visibleLinks = [];
    graphState.selectedNode = null;
    graphState.hoveredNode = null;
    graphState.impactPaths.upstream.clear();
    graphState.impactPaths.downstream.clear();
    graphState.codeAreaNodes.clear();
    graphState.aiHighlightedNodes.clear();

    if (!projectId) {
        drawGraphMessage("Select a project to visualize the Knowledge Graph.");
        return;
    }

    drawGraphMessage("Loading project graph data...");

    try {
        const [projectData, mappings, methodCalls] = await Promise.all([
            apiCall(`/api/projects/${projectId}/full`),
            apiCall(`/api/projects/${projectId}/mappings`),
            apiCall(`/api/projects/${projectId}/method-calls`)
        ]);

        buildGraph(projectData, mappings, methodCalls);
        resetGraphView();
        runGraphSimulation();
    } catch (err) {
        console.error("Failed to load graph data", err);
        drawGraphMessage("Failed to load knowledge graph data. Check server logs.");
    }
}

function buildGraph(projectData, mappings, methodCalls) {
    const nodesMap = new Map();
    const linksList = [];

    // Colors & Sizes
    const colors = {
        PROJECT: '#8b5cf6',
        EPIC: '#f97316',
        FEATURE: '#3b82f6',
        REQUIREMENT: '#eab308',
        TASK: '#14b8a6',
        CLASS: '#ef4444',
        METHOD: '#10b981'
    };

    const radii = {
        PROJECT: 35,
        EPIC: 28,
        FEATURE: 22,
        REQUIREMENT: 22,
        TASK: 16,
        CLASS: 12,
        METHOD: 12
    };

    // 1. Add Project Node
    const pId = `PROJECT_${projectData.id}`;
    nodesMap.set(pId, {
        id: pId,
        rawId: projectData.id,
        type: 'PROJECT',
        label: projectData.name,
        color: colors.PROJECT,
        radius: radii.PROJECT,
        x: 0, y: 0, vx: 0, vy: 0,
        status: projectData.status,
        meta: projectData
    });

    // 2. Add Epics, Features, Requirements, Tasks
    (projectData.epics || []).forEach(epic => {
        const epId = `EPIC_${epic.id}`;
        nodesMap.set(epId, {
            id: epId,
            rawId: epic.id,
            type: 'EPIC',
            label: epic.name,
            color: colors.EPIC,
            radius: radii.EPIC,
            x: (Math.random() - 0.5) * 100, y: (Math.random() - 0.5) * 100, vx: 0, vy: 0,
            status: epic.status,
            meta: epic
        });
        linksList.push({ source: pId, target: epId, type: 'BUSINESS' });

        (epic.features || []).forEach(feat => {
            const fId = `FEATURE_${feat.id}`;
            nodesMap.set(fId, {
                id: fId,
                rawId: feat.id,
                type: 'FEATURE',
                label: feat.name,
                color: colors.FEATURE,
                radius: radii.FEATURE,
                x: (Math.random() - 0.5) * 150, y: (Math.random() - 0.5) * 150, vx: 0, vy: 0,
                status: feat.status,
                meta: feat
            });
            linksList.push({ source: epId, target: fId, type: 'BUSINESS' });

            (feat.requirements || []).forEach(req => {
                const rId = `REQUIREMENT_${req.id}`;
                nodesMap.set(rId, {
                    id: rId,
                    rawId: req.id,
                    type: 'REQUIREMENT',
                    label: req.title,
                    color: colors.REQUIREMENT,
                    radius: radii.REQUIREMENT,
                    x: (Math.random() - 0.5) * 200, y: (Math.random() - 0.5) * 200, vx: 0, vy: 0,
                    status: req.status,
                    meta: req
                });
                linksList.push({ source: fId, target: rId, type: 'BUSINESS' });
            });

            (feat.tasks || []).forEach(task => {
                const tId = `TASK_${task.id}`;
                nodesMap.set(tId, {
                    id: tId,
                    rawId: task.id,
                    type: 'TASK',
                    label: task.title,
                    color: colors.TASK,
                    radius: radii.TASK,
                    x: (Math.random() - 0.5) * 250, y: (Math.random() - 0.5) * 250, vx: 0, vy: 0,
                    status: task.status,
                    meta: task
                });
                
                if (task.requirementId) {
                    linksList.push({ source: `REQUIREMENT_${task.requirementId}`, target: tId, type: 'BUSINESS' });
                } else {
                    linksList.push({ source: fId, target: tId, type: 'BUSINESS' });
                }
            });
        });
    });

    // 3. Add Classes and Methods from codebase mappings
    (mappings || []).forEach(m => {
        if (m.javaClass) {
            const clId = `CLASS_${m.javaClass.id}`;
            if (!nodesMap.has(clId)) {
                nodesMap.set(clId, {
                    id: clId,
                    rawId: m.javaClass.id,
                    type: 'CLASS',
                    label: m.javaClass.name,
                    color: colors.CLASS,
                    radius: radii.CLASS,
                    x: (Math.random() - 0.5) * 350, y: (Math.random() - 0.5) * 350, vx: 0, vy: 0,
                    status: m.javaClass.packageName,
                    meta: m.javaClass
                });
            }

            if (m.task) {
                linksList.push({ source: `TASK_${m.task.id}`, target: clId, type: 'MAPPING' });
            }
            if (m.requirement) {
                linksList.push({ source: `REQUIREMENT_${m.requirement.id}`, target: clId, type: 'MAPPING' });
            }
        }

        if (m.javaMethod && m.javaClass) {
            const meId = `METHOD_${m.javaMethod.id}`;
            const clId = `CLASS_${m.javaClass.id}`;
            if (!nodesMap.has(meId)) {
                nodesMap.set(meId, {
                    id: meId,
                    rawId: m.javaMethod.id,
                    type: 'METHOD',
                    label: m.javaMethod.name,
                    color: colors.METHOD,
                    radius: radii.METHOD,
                    x: (Math.random() - 0.5) * 400, y: (Math.random() - 0.5) * 400, vx: 0, vy: 0,
                    status: m.javaMethod.returnType,
                    meta: m.javaMethod
                });
            }

            linksList.push({ source: clId, target: meId, type: 'STRUCTURE' });

            if (m.task) {
                linksList.push({ source: `TASK_${m.task.id}`, target: meId, type: 'MAPPING' });
            }
            if (m.requirement) {
                linksList.push({ source: `REQUIREMENT_${m.requirement.id}`, target: meId, type: 'MAPPING' });
            }
        }
    });

    // 4. Add Method Calls
    (methodCalls || []).forEach(call => {
        if (call.callerMethod && call.calleeMethod) {
            const callerId = `METHOD_${call.callerMethod.id}`;
            const calleeId = `METHOD_${call.calleeMethod.id}`;
            if (nodesMap.has(callerId) && nodesMap.has(calleeId)) {
                linksList.push({ source: callerId, target: calleeId, type: 'CALL_GRAPH' });
            }
        }
    });

    graphState.nodes = Array.from(nodesMap.values());
    graphState.links = linksList;
    
    applyGraphFilters();
}

function applyGraphFilters() {
    const filter = graphState.activeFilters;
    
    // Build child mapping based on source/target links
    const childrenMap = {};
    graphState.links.forEach(l => {
        if (!childrenMap[l.source]) childrenMap[l.source] = [];
        childrenMap[l.source].push(l.target);
    });

    // Recursively mark all hidden descendants of collapsed nodes
    const hiddenNodes = new Set();
    const dfsHide = (nodeId) => {
        const children = childrenMap[nodeId] || [];
        children.forEach(childId => {
            if (!hiddenNodes.has(childId)) {
                hiddenNodes.add(childId);
                dfsHide(childId);
            }
        });
    };

    if (graphState.collapsedNodes) {
        graphState.collapsedNodes.forEach(nodeId => {
            dfsHide(nodeId);
        });
    }

    // Filter nodes
    graphState.visibleNodes = graphState.nodes.filter(n => {
        if (hiddenNodes.has(n.id)) return false;
        
        if (n.type === 'PROJECT') return true;
        if (n.type === 'EPIC' || n.type === 'FEATURE') return filter.EPIC;
        if (n.type === 'REQUIREMENT') return filter.REQUIREMENT;
        if (n.type === 'TASK') return filter.TASK;
        if (n.type === 'CLASS') return filter.CLASS;
        if (n.type === 'METHOD') return filter.METHOD;
        return true;
    });

    const visibleIds = new Set(graphState.visibleNodes.map(n => n.id));

    // Filter links
    graphState.visibleLinks = graphState.links.filter(l => {
        return visibleIds.has(l.source) && visibleIds.has(l.target);
    });
}

function runGraphSimulation() {
    const canvas = document.getElementById("graph-canvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");

    const resizeCanvas = () => {
        const container = canvas.parentElement;
        if (container) {
            canvas.width = container.clientWidth;
            canvas.height = container.clientHeight;
        }
    };
    resizeCanvas();

    const loop = () => {
        // 1. Update Physics
        updateGraphPhysics();

        // 2. Draw
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        drawGraph(ctx, canvas.width, canvas.height);

        graphState.animFrameId = requestAnimationFrame(loop);
    };
    graphState.animFrameId = requestAnimationFrame(loop);
}

function updateGraphPhysics() {
    const nodes = graphState.visibleNodes;
    const links = graphState.visibleLinks;
    if (nodes.length === 0) return;

    const gravityConst = 0.005; // Lower gravity allows graph expansion
    const springLengthConst = 140; // Wider base connection length
    const springCoeffConst = 0.04;
    const repulsionConst = 15000; // Much stronger repulsion force

    // 1. Repulsion & Collision Prevention
    for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
            const n1 = nodes[i];
            const n2 = nodes[j];
            const dx = n2.x - n1.x;
            const dy = n2.y - n1.y;
            let dist = Math.hypot(dx, dy);
            if (dist === 0) dist = 0.1;

            const minSeparation = n1.radius + n2.radius + 80;
            let fx = 0;
            let fy = 0;

            if (dist < minSeparation) {
                // Hard push to prevent overlap
                const overlap = minSeparation - dist;
                const force = overlap * 0.8;
                fx = (dx / dist) * force;
                fy = (dy / dist) * force;
            } else {
                // Standard inverse-square repulsion
                const force = repulsionConst / (dist * dist);
                fx = (dx / dist) * force;
                fy = (dy / dist) * force;
            }

            n1.vx -= fx;
            n1.vy -= fy;
            n2.vx += fx;
            n2.vy += fy;
        }
    }

    // 2. Link Spring Attraction (only attracts if nodes are far apart)
    for (let link of links) {
        const sourceNode = nodes.find(n => n.id === link.source);
        const targetNode = nodes.find(n => n.id === link.target);
        if (!sourceNode || !targetNode) continue;

        const dx = targetNode.x - sourceNode.x;
        const dy = targetNode.y - sourceNode.y;
        const dist = Math.hypot(dx, dy);
        if (dist === 0) continue;

        if (dist > springLengthConst) {
            const force = (dist - springLengthConst) * springCoeffConst;
            const fx = (dx / dist) * force;
            const fy = (dy / dist) * force;

            sourceNode.vx += fx;
            sourceNode.vy += fy;
            targetNode.vx -= fx;
            targetNode.vy -= fy;
        }
    }

    // 3. Gravity, Y-Tier pull, Friction & Movement
    const tierY = {
        'PROJECT': -240,
        'EPIC': -160,
        'FEATURE': -80,
        'REQUIREMENT': 40,
        'TASK': 120,
        'CLASS': 220,
        'METHOD': 320
    };
    const tierPullConst = 0.08; // Gentle pull to align into clean rows without losing fluid horizontal movement

    for (let node of nodes) {
        if (node === graphState.dragNode) continue;

        // Apply Y-tier centering pull
        const targetY = tierY[node.type] !== undefined ? tierY[node.type] : 0;
        node.vy += (targetY - node.y) * tierPullConst;

        node.vx -= node.x * gravityConst;

        node.vx *= graphState.friction;
        node.vy *= graphState.friction;
        node.x += node.vx;
        node.y += node.vy;
    }
}

function drawGraph(ctx, width, height) {
    ctx.save();
    // Apply pan & zoom
    ctx.translate(graphState.transform.x + width / 2, graphState.transform.y + height / 2);
    ctx.scale(graphState.transform.k, graphState.transform.k);

    const hasAiHighlights = (graphState.aiHighlightedNodes.size > 0);
    const hasSelection = (graphState.selectedNode !== null) || hasAiHighlights;
    const hasCodeArea = (graphState.codeAreaNodes.size > 0);

    // 1. Draw Links
    graphState.visibleLinks.forEach(link => {
        const sourceNode = graphState.visibleNodes.find(n => n.id === link.source);
        const targetNode = graphState.visibleNodes.find(n => n.id === link.target);
        if (!sourceNode || !targetNode) return;

        let opacity = 0.15;
        let color = '#4b5563'; // Muted gray
        let lineWidth = 1;

        if (hasSelection) {
            const isUpstreamLink = (graphState.impactPaths.upstream.has(link.source) && graphState.impactPaths.upstream.has(link.target));
            const isDownstreamLink = (graphState.impactPaths.downstream.has(link.source) && graphState.impactPaths.downstream.has(link.target));
            const isSelectedConnector = graphState.selectedNode && (link.source === graphState.selectedNode.id || link.target === graphState.selectedNode.id);
            const isCodeAreaConnector = (graphState.codeAreaNodes.has(link.source) && graphState.codeAreaNodes.has(link.target));
            const isAiConnector = (graphState.aiHighlightedNodes.has(link.source) && graphState.aiHighlightedNodes.has(link.target));

            if (isUpstreamLink || (isSelectedConnector && graphState.impactPaths.upstream.has(link.target))) {
                color = '#f97316'; // Orange impact path
                opacity = 0.8;
                lineWidth = 2.5;
            } else if (isDownstreamLink || (isSelectedConnector && graphState.impactPaths.downstream.has(link.target))) {
                color = '#3b82f6'; // Blue impact path
                opacity = 0.8;
                lineWidth = 2.5;
            } else if (isCodeAreaConnector || (isSelectedConnector && graphState.codeAreaNodes.has(link.target))) {
                color = '#14b8a6'; // Code Area highlight
                opacity = 0.7;
                lineWidth = 2;
            } else if (isAiConnector || (hasAiHighlights && (graphState.aiHighlightedNodes.has(link.source) || graphState.aiHighlightedNodes.has(link.target)))) {
                color = '#8b5cf6'; // Violet AI highlight path
                opacity = 0.7;
                lineWidth = 2;
            } else {
                opacity = 0.04;
            }
        }

        ctx.strokeStyle = color;
        ctx.globalAlpha = opacity;
        ctx.lineWidth = lineWidth;
        ctx.beginPath();
        ctx.moveTo(sourceNode.x, sourceNode.y);
        ctx.lineTo(targetNode.x, targetNode.y);
        
        if (link.type === 'CALL_GRAPH' || link.type === 'MAPPING') {
            ctx.setLineDash([4, 4]);
        } else {
            ctx.setLineDash([]);
        }
        ctx.stroke();
        
        // 2. Draw Impact Flow Particles
        if (hasSelection && link.type === 'CALL_GRAPH' && (color === '#f97316' || color === '#3b82f6')) {
            ctx.globalAlpha = 1.0;
            ctx.setLineDash([]);
            ctx.fillStyle = color;
            const t = (Date.now() / 1500) % 1;
            // Particles flow caller -> callee (which is source -> target)
            const px = sourceNode.x + (targetNode.x - sourceNode.x) * t;
            const py = sourceNode.y + (targetNode.y - sourceNode.y) * t;
            ctx.beginPath();
            ctx.arc(px, py, 3.5, 0, 2 * Math.PI);
            ctx.fill();
        }
    });

    ctx.setLineDash([]);
    ctx.globalAlpha = 1.0;

    // 3. Draw Nodes
    graphState.visibleNodes.forEach(node => {
        let opacity = 1.0;
        let isHighlighted = false;

        if (hasSelection) {
            const isSelected = graphState.selectedNode && (node.id === graphState.selectedNode.id);
            const isUpstream = graphState.impactPaths.upstream.has(node.id);
            const isDownstream = graphState.impactPaths.downstream.has(node.id);
            const isCodeArea = graphState.codeAreaNodes.has(node.id);
            const isAiHighlighted = graphState.aiHighlightedNodes.has(node.id);

            if (isSelected || isUpstream || isDownstream || isCodeArea || isAiHighlighted) {
                isHighlighted = true;
            } else {
                opacity = 0.15;
            }
        }

        if (graphState.hoveredNode && node.id === graphState.hoveredNode.id) {
            isHighlighted = true;
        }

        ctx.globalAlpha = opacity;

        // Draw Glow
        if (isHighlighted) {
            ctx.shadowBlur = 15;
            ctx.shadowColor = node.color;
        } else {
            ctx.shadowBlur = 0;
        }

        // Draw Circle
        ctx.fillStyle = node.color;
        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius, 0, 2 * Math.PI);
        ctx.fill();
        
        ctx.shadowBlur = 0; // Reset shadow

        // Draw outer status border ring
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
        ctx.lineWidth = 1.5;
        
        if (node.type === 'CLASS' || node.type === 'METHOD') {
            ctx.setLineDash([3, 3]); // Dashed line for codebase mappings
            ctx.strokeStyle = node.type === 'CLASS' ? '#ef4444' : '#10b981';
            ctx.lineWidth = 2.0;
        } else if (node.type === 'TASK' || node.type === 'REQUIREMENT') {
            ctx.setLineDash([]);
            const status = (node.status || '').toUpperCase();
            if (status === 'SUCCESS' || status === 'COMPLETED' || status === 'DONE') {
                ctx.strokeStyle = '#10b981'; // Green ring for success
                ctx.lineWidth = 2.5;
            } else if (status === 'IN_PROGRESS' || status === 'TESTING' || status === 'PLANNING') {
                ctx.strokeStyle = '#f59e0b'; // Amber ring for active
                ctx.lineWidth = 2.5;
            }
        } else {
            ctx.setLineDash([]);
        }

        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius + 3.5, 0, 2 * Math.PI);
        ctx.stroke();
        ctx.setLineDash([]); // Reset line dash

        // Draw indicator for collapsed nodes
        if (graphState.collapsedNodes && graphState.collapsedNodes.has(node.id)) {
            // Draw secondary outer glowing ring
            ctx.strokeStyle = 'rgba(139, 92, 246, 0.6)';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.arc(node.x, node.y, node.radius + 7, 0, 2 * Math.PI);
            ctx.stroke();

            // Draw a tiny '+' inside a circle on the top-right
            ctx.fillStyle = '#8b5cf6';
            ctx.beginPath();
            ctx.arc(node.x + node.radius - 2, node.y - node.radius + 2, 6, 0, 2 * Math.PI);
            ctx.fill();

            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold 9px sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('+', node.x + node.radius - 2, node.y - node.radius + 2);
        }

        // Draw Label text
        ctx.fillStyle = '#f3f4f6';
        ctx.font = node.type === 'PROJECT' ? 'bold 11px sans-serif' : '10px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.shadowColor = '#000000';
        ctx.shadowBlur = 4;
        
        const labelText = node.label.length > 18 ? node.label.substring(0, 15) + "..." : node.label;
        ctx.fillText(labelText, node.x, node.y + node.radius + 6);
        ctx.shadowBlur = 0; // Reset shadow
    });

    ctx.restore();
}

function drawGraphMessage(msg) {
    const canvas = document.getElementById("graph-canvas");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = "#9ca3af";
    ctx.font = "14px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(msg, canvas.width / 2, canvas.height / 2);
}

function initGraphCanvasListeners() {
    if (graphInitialized) return;
    const canvas = document.getElementById("graph-canvas");
    if (!canvas) return;

    canvas.addEventListener("mousedown", handleGraphMouseDown);
    canvas.addEventListener("mousemove", handleGraphMouseMove);
    window.addEventListener("mouseup", handleGraphMouseUp);
    canvas.addEventListener("wheel", handleGraphWheel);
    canvas.addEventListener("dblclick", handleGraphDoubleClick);

    graphInitialized = true;
}

function handleGraphDoubleClick(event) {
    const pos = getGraphMousePos(event);
    
    let clickedNode = null;
    for (let n of graphState.visibleNodes) {
        const dist = Math.hypot(n.x - pos.x, n.y - pos.y);
        if (dist <= n.radius + 5) {
            clickedNode = n;
            break;
        }
    }

    if (clickedNode) {
        event.preventDefault();
        event.stopPropagation();
        if (graphState.collapsedNodes.has(clickedNode.id)) {
            graphState.collapsedNodes.delete(clickedNode.id);
        } else {
            graphState.collapsedNodes.add(clickedNode.id);
        }
        applyGraphFilters();
    }
}

function getGraphMousePos(event) {
    const canvas = document.getElementById("graph-canvas");
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left - canvas.width / 2 - graphState.transform.x) / graphState.transform.k;
    const y = (event.clientY - rect.top - canvas.height / 2 - graphState.transform.y) / graphState.transform.k;
    return { x, y };
}

function handleGraphMouseDown(event) {
    const pos = getGraphMousePos(event);
    
    // Check if clicked node
    let clickedNode = null;
    for (let n of graphState.visibleNodes) {
        const dist = Math.hypot(n.x - pos.x, n.y - pos.y);
        if (dist <= n.radius + 5) {
            clickedNode = n;
            break;
        }
    }

    if (clickedNode) {
        graphState.dragNode = clickedNode;
        graphState.selectedNode = clickedNode;
        calculateNodeRelations(clickedNode);
        showGraphNodeDetails(clickedNode);
    } else {
        graphState.dragNode = null;
        graphState.dragStart = { x: event.clientX, y: event.clientY };
        graphState.dragOffset = { x: graphState.transform.x, y: graphState.transform.y };
    }
}

function handleGraphMouseMove(event) {
    if (graphState.dragNode) {
        const pos = getGraphMousePos(event);
        graphState.dragNode.x = pos.x;
        graphState.dragNode.y = pos.y;
    } else if (graphState.dragStart) {
        const dx = event.clientX - graphState.dragStart.x;
        const dy = event.clientY - graphState.dragStart.y;
        graphState.transform.x = graphState.dragOffset.x + dx;
        graphState.transform.y = graphState.dragOffset.y + dy;
    } else {
        // Hover check
        const pos = getGraphMousePos(event);
        let hovered = null;
        for (let n of graphState.visibleNodes) {
            const dist = Math.hypot(n.x - pos.x, n.y - pos.y);
            if (dist <= n.radius + 5) {
                hovered = n;
                break;
            }
        }
        graphState.hoveredNode = hovered;
    }
}

function handleGraphMouseUp() {
    graphState.dragNode = null;
    graphState.dragStart = null;
}

function handleGraphWheel(event) {
    event.preventDefault();
    const zoomFactor = 1.1;
    if (event.deltaY < 0) {
        graphState.transform.k *= zoomFactor;
    } else {
        graphState.transform.k /= zoomFactor;
    }
    graphState.transform.k = Math.max(0.2, Math.min(graphState.transform.k, 4.0));
}

function calculateNodeRelations(node) {
    graphState.impactPaths.upstream.clear();
    graphState.impactPaths.downstream.clear();
    graphState.codeAreaNodes.clear();

    if (!node) return;

    // Helper functions for traversals
    const findCodeArea = (startNodeId) => {
        const visited = new Set();
        const queue = [startNodeId];
        while (queue.length > 0) {
            const currId = queue.shift();
            if (visited.has(currId)) continue;
            visited.add(currId);

            graphState.visibleLinks.forEach(link => {
                if (link.source === currId && (link.type === 'MAPPING' || link.type === 'STRUCTURE')) {
                    graphState.codeAreaNodes.add(link.target);
                    queue.push(link.target);
                }
            });
        }
    };

    const findUpstreamCallers = (startNodeId) => {
        const visited = new Set();
        const queue = [startNodeId];
        while (queue.length > 0) {
            const currId = queue.shift();
            if (visited.has(currId)) continue;
            visited.add(currId);

            graphState.visibleLinks.forEach(link => {
                if (link.target === currId && link.type === 'CALL_GRAPH') {
                    graphState.impactPaths.upstream.add(link.source);
                    queue.push(link.source);
                } else if (link.target === currId && link.type === 'MAPPING') {
                    // Task or Requirement owning this code
                    graphState.impactPaths.upstream.add(link.source);
                }
            });
        }
    };

    const findDownstreamCallees = (startNodeId) => {
        const visited = new Set();
        const queue = [startNodeId];
        while (queue.length > 0) {
            const currId = queue.shift();
            if (visited.has(currId)) continue;
            visited.add(currId);

            graphState.visibleLinks.forEach(link => {
                if (link.source === currId && link.type === 'CALL_GRAPH') {
                    graphState.impactPaths.downstream.add(link.target);
                    queue.push(link.target);
                }
            });
        }
    };

    // Trigger traversals based on type
    if (node.type === 'TASK' || node.type === 'REQUIREMENT') {
        findCodeArea(node.id);
    } else if (node.type === 'METHOD' || node.type === 'CLASS') {
        findUpstreamCallers(node.id);
        findDownstreamCallees(node.id);
    }
}

function showGraphNodeDetails(node) {
    const panel = document.getElementById("graph-details-panel");
    if (!panel) return;

    panel.classList.remove("hidden-element");
    
    let detailHtml = `
        <div class="graph-details-header">
            <h3>${escapeHtml(node.label)}</h3>
            <span class="graph-details-type" style="background:${node.color}; color:#fff;">${node.type}</span>
        </div>
        <div class="graph-details-body">
    `;

    if (node.type === 'PROJECT') {
        detailHtml += `
            <div class="graph-details-section">
                <span class="graph-details-label">Status</span>
                <span class="graph-details-value">${escapeHtml(node.meta.status)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">Difficulty</span>
                <span class="graph-details-value">${escapeHtml(node.meta.difficulty)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">Description</span>
                <span class="graph-details-value">${escapeHtml(node.meta.description || 'No description')}</span>
            </div>
        `;
    } else if (node.type === 'EPIC' || node.type === 'FEATURE') {
        detailHtml += `
            <div class="graph-details-section">
                <span class="graph-details-label">Status</span>
                <span class="graph-details-value">${escapeHtml(node.meta.status)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">Description</span>
                <span class="graph-details-value">${escapeHtml(node.meta.description || 'No description')}</span>
            </div>
        `;
    } else if (node.type === 'REQUIREMENT') {
        detailHtml += `
            <div class="graph-details-section">
                <span class="graph-details-label">Type / Complexity</span>
                <span class="graph-details-value">${escapeHtml(node.meta.type)} - ${escapeHtml(node.meta.complexity)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">Description</span>
                <span class="graph-details-value">${escapeHtml(node.meta.description)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">User Story</span>
                <span class="graph-details-value" style="font-style: italic;">"${escapeHtml(node.meta.userStory ? node.meta.userStory.storyText : 'None')}"</span>
            </div>
        `;
    } else if (node.type === 'TASK') {
        detailHtml += `
            <div class="graph-details-section">
                <span class="graph-details-label">Status / Priority</span>
                <span class="graph-details-value">${escapeHtml(node.meta.status)} - ${escapeHtml(node.meta.priority)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">Description</span>
                <span class="graph-details-value">${escapeHtml(node.meta.description)}</span>
            </div>
        `;
        if (node.meta.hasCodeCandidate) {
            detailHtml += `
                <button class="btn btn-primary btn-sm margin-top-16" onclick="loadCandidateReview('${node.meta.id}')">Open Code Review</button>
            `;
        }
    } else if (node.type === 'CLASS') {
        detailHtml += `
            <div class="graph-details-section">
                <span class="graph-details-label">Package Name</span>
                <span class="graph-details-value">${escapeHtml(node.status)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">File Path</span>
                <span class="graph-details-value" style="font-family:monospace; font-size:11px;">${escapeHtml(node.meta.filePath)}</span>
            </div>
        `;
    } else if (node.type === 'METHOD') {
        detailHtml += `
            <div class="graph-details-section">
                <span class="graph-details-label">Return Type</span>
                <span class="graph-details-value">${escapeHtml(node.status)}</span>
            </div>
            <div class="graph-details-section">
                <span class="graph-details-label">Signature</span>
                <span class="graph-details-value" style="font-family:monospace; font-size:11px;">${escapeHtml(node.meta.signature)}</span>
            </div>
        `;
    }

    // Add Dependency and Change Impact Risk section for Code elements
    if (node.type === 'METHOD' || node.type === 'CLASS') {
        const upstreamCount = graphState.impactPaths.upstream.size;
        const downstreamCount = graphState.impactPaths.downstream.size;
        let riskRating = 'LOW';
        let riskClass = 'risk-low';
        
        if (upstreamCount > 3) {
            riskRating = 'HIGH';
            riskClass = 'risk-high';
        } else if (upstreamCount > 0) {
            riskRating = 'MEDIUM';
            riskClass = 'risk-medium';
        }

        detailHtml += `
            <div style="margin-top: 15px; padding-top: 15px; border-top: 1px solid var(--border-color);">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                    <span class="graph-details-label">Impact Risk Assessment</span>
                    <span class="risk-badge ${riskClass}">${riskRating}</span>
                </div>
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-bottom:12px;">
                    <div style="background:rgba(255,255,255,0.02); border:1px solid var(--border-color); padding:8px; border-radius:4px; text-align:center;">
                        <div style="font-size:18px; font-weight:700; color:#f97316;">${upstreamCount}</div>
                        <div style="font-size:9px; color:var(--text-muted); text-transform:uppercase;">Upstream Callers</div>
                    </div>
                    <div style="background:rgba(255,255,255,0.02); border:1px solid var(--border-color); padding:8px; border-radius:4px; text-align:center;">
                        <div style="font-size:18px; font-weight:700; color:#3b82f6;">${downstreamCount}</div>
                        <div style="font-size:9px; color:var(--text-muted); text-transform:uppercase;">Downstream Callees</div>
                    </div>
                </div>
            </div>
        `;
    }

    detailHtml += `</div>`;
    panel.innerHTML = detailHtml;
}

function toggleGraphLayer(type, checked) {
    graphState.activeFilters[type] = checked;
    applyGraphFilters();
}

function handleGraphSearch(value) {
    graphState.searchQuery = (value || '').trim().toLowerCase();
    if (!graphState.searchQuery) {
        graphState.selectedNode = null;
        graphState.impactPaths.upstream.clear();
        graphState.impactPaths.downstream.clear();
        graphState.codeAreaNodes.clear();
        return;
    }

    // Find first matching node
    const match = graphState.visibleNodes.find(n => n.label.toLowerCase().includes(graphState.searchQuery));
    if (match) {
        graphState.selectedNode = match;
        calculateNodeRelations(match);
        showGraphNodeDetails(match);
        // Center view on this node
        graphState.transform.x = -match.x * graphState.transform.k;
        graphState.transform.y = -match.y * graphState.transform.k;
    }
}

function resetGraphView() {
    graphState.transform = { x: 0, y: 0, k: 1 };
    
    // Give nodes a slight random positioning to start the physics web simulation nicely
    graphState.visibleNodes.forEach(node => {
        if (node.type === 'PROJECT') {
            node.x = 0; node.y = 0;
        } else {
            node.x = (Math.random() - 0.5) * 300;
            node.y = (Math.random() - 0.5) * 300;
        }
        node.vx = 0;
        node.vy = 0;
    });
}

async function scanProjectFromGraph() {
    const projectId = document.getElementById("graph-project-select").value;
    if (!projectId) {
        alert("Please select a project to scan.");
        return;
    }
    
    drawGraphMessage("Scanning codebase repository in background...");
    
    try {
        await apiCall(`/api/projects/${projectId}/scan`, 'POST');
        alert("Codebase scan complete! Refreshing graph layout.");
        loadGraphData(projectId);
    } catch (err) {
        alert(`Scan failed: ${err.message}`);
        loadGraphData(projectId);
    }
}

// Global Search debouncing and API mapping
let globalSearchTimeout = null;

function handleGlobalSearchInput(value) {
    clearTimeout(globalSearchTimeout);
    globalSearchTimeout = setTimeout(() => {
        triggerGlobalSearch();
    }, 300);
}

function resetSearchFilters() {
    // Reset radios
    const radios = document.querySelectorAll('input[name="search-completion"]');
    radios.forEach((r, idx) => r.checked = (idx === 0));

    // Reset checkboxes
    document.querySelectorAll('#search-priority-filters input').forEach(c => c.checked = false);
    document.querySelectorAll('#search-status-filters input').forEach(c => c.checked = false);

    triggerGlobalSearch();
}

async function triggerGlobalSearch() {
    const query = document.getElementById("global-search-input") ? document.getElementById("global-search-input").value : "";
    const container = document.getElementById("search-results-container");
    if (!container) return;

    // Collect filters
    const completionStateRadio = document.querySelector('input[name="search-completion"]:checked');
    const completionState = completionStateRadio ? completionStateRadio.value : "";

    const priorities = [];
    document.querySelectorAll('#search-priority-filters input:checked').forEach(c => priorities.push(c.value));

    const statuses = [];
    document.querySelectorAll('#search-status-filters input:checked').forEach(c => statuses.push(c.value));

    // Build URL parameters
    const params = new URLSearchParams();
    if (query.trim()) params.append("query", query);
    if (completionState) params.append("completionState", completionState);
    priorities.forEach(p => params.append("priority", p));
    statuses.forEach(s => params.append("status", s));

    try {
        const results = await apiCall(`/api/search?${params.toString()}`);
        renderSearchResults(results);
    } catch (err) {
        console.error("Global search failed", err);
        container.innerHTML = `<div class="error-message"><p>Search query failed. Check server logs.</p></div>`;
    }
}

function renderSearchResults(results) {
    const container = document.getElementById("search-results-container");
    if (!container) return;
    container.innerHTML = "";

    const hasProjects = results.projects && results.projects.length > 0;
    const hasEpics = results.epics && results.epics.length > 0;
    const hasFeatures = results.features && results.features.length > 0;
    const hasRequirements = results.requirements && results.requirements.length > 0;
    const hasTasks = results.tasks && results.tasks.length > 0;
    const hasTech = results.technologies && results.technologies.length > 0;

    if (!hasProjects && !hasEpics && !hasFeatures && !hasRequirements && !hasTasks && !hasTech) {
        container.innerHTML = `
            <div class="search-empty-state">
                <span class="search-empty-icon">🔍</span>
                <p class="muted-text">No matches found. Try modifying your filters or query term.</p>
            </div>
        `;
        return;
    }

    // 1. Projects
    if (hasProjects) {
        renderCategory(container, "📁 Matches in Projects", results.projects, (p) => {
            return {
                title: p.name,
                desc: p.description,
                meta: `Category: ${p.category} | Difficulty: ${p.difficulty} | Status: ${p.status}`,
                click: () => {
                    loadProjectDetails(p.id);
                    switchTab('projects');
                }
            };
        });
    }

    // 2. Epics
    if (hasEpics) {
        renderCategory(container, "⚡ Matches in Epics", results.epics, (e) => {
            return {
                title: e.name,
                desc: e.description,
                meta: `Priority: ${e.priority} | Status: ${e.status} | Progress: ${e.progress || 0}%`,
                click: () => {
                    loadProjectDetails(e.projectId);
                    switchTab('projects');
                }
            };
        });
    }

    // 3. Features
    if (hasFeatures) {
        renderCategory(container, "⚡ Matches in Features", results.features, (f) => {
            return {
                title: f.name,
                desc: f.description,
                meta: `Priority: ${f.priority} | Status: ${f.status} | Tasks: ${f.taskCount || 0}`,
                click: () => {
                    loadProjectDetails(f.projectId).then(() => {
                        switchTab('projects');
                        selectExplorerFeature(f.id, f.name);
                    });
                }
            };
        });
    }

    // 4. Requirements
    if (hasRequirements) {
        renderCategory(container, "📌 Matches in Requirements", results.requirements, (r) => {
            return {
                title: r.title,
                desc: r.description,
                meta: `Complexity: ${r.complexity} | Priority: ${r.priority} | Status: ${r.status}`,
                click: () => {
                    loadProjectDetails(r.projectId).then(() => {
                        switchTab('projects');
                        selectExplorerFeature(r.featureId, r.featureName || 'Feature');
                        setTimeout(() => {
                            const treeItem = document.querySelector(`#tree-node-requirement-${r.id} .tree-node-header`);
                            if (treeItem) treeItem.click();
                        }, 200);
                    });
                }
            };
        });
    }

    // 5. Tasks
    if (hasTasks) {
        renderCategory(container, "⚙ Matches in Tasks", results.tasks, (t) => {
            return {
                title: t.title,
                desc: t.description,
                meta: `Priority: ${t.priority} | Status: ${t.status} | Feature: ${t.featureName || 'N/A'}`,
                click: () => {
                    loadKanbanData(t.projectId || state.activeProjectId).then(() => {
                        switchTab('kanban');
                        setTimeout(() => {
                            const card = document.querySelector(`[data-task-id="${t.id}"]`);
                            if (card) {
                                card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                                card.style.boxShadow = '0 0 15px rgba(20, 184, 166, 0.7)';
                                setTimeout(() => card.style.boxShadow = '', 2500);
                            }
                        }, 200);
                    });
                }
            };
        });
    }

    // 6. Technologies
    if (hasTech) {
        renderCategory(container, "🔧 Matches in Technologies", results.technologies, (tech) => {
            return {
                title: tech.name,
                desc: tech.description,
                meta: `Category: ${tech.category} | Version: ${tech.version || 'Latest'}`,
                click: () => {
                    switchTab('technologies');
                    setTimeout(() => {
                        const card = document.getElementById(`tech-card-${tech.id}`);
                        if (card) {
                            card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                            card.style.boxShadow = '0 0 15px rgba(59, 130, 246, 0.7)';
                            setTimeout(() => card.style.boxShadow = '', 2500);
                        }
                    }, 200);
                }
            };
        });
    }
}

function renderCategory(parent, title, items, mapFn) {
    const catBlock = document.createElement("div");
    catBlock.className = "search-result-category";

    catBlock.innerHTML = `<div class="search-result-category-title">${escapeHtml(title)}</div>`;
    
    const itemsList = document.createElement("div");
    itemsList.className = "search-result-items-list";

    items.forEach(item => {
        const mapped = mapFn(item);
        const card = document.createElement("div");
        card.className = "search-result-item-card glass-card";
        card.onclick = mapped.click;

        card.innerHTML = `
            <div class="search-result-item-header">
                <div class="search-result-item-title">${escapeHtml(mapped.title)}</div>
            </div>
            ${mapped.desc ? `<div class="search-result-item-desc">${escapeHtml(mapped.desc)}</div>` : ''}
            <div class="search-result-item-meta">${escapeHtml(mapped.meta)}</div>
        `;
        itemsList.appendChild(card);
    });

    catBlock.appendChild(itemsList);
    parent.appendChild(catBlock);
}

// AI Assistant (Project Intelligence) Panel Logic
let intelligenceProjectSelectInitialized = false;

function populateIntelligenceProjectSelectors() {
    const selector = document.getElementById("intelligence-project-select");
    if (!selector) return;
    
    // Repopulate projects
    selector.innerHTML = '<option value="">-- Select Project --</option>';
    (state.projects || []).forEach(p => {
        const opt = document.createElement("option");
        opt.value = p.id;
        opt.innerText = p.name;
        selector.appendChild(opt);
    });

    if (state.activeProjectId) {
        selector.value = state.activeProjectId;
    }
}

function handleIntelligenceProjectChange(projectId) {
    if (projectId) {
        state.activeProjectId = projectId;
        // Keep other panels selectors synced
        const pSel = document.getElementById("project-select");
        if (pSel) pSel.value = projectId;
        const kSel = document.getElementById("kanban-project-select");
        if (kSel) kSel.value = projectId;
        
        loadGraphData(projectId);
    } else {
        loadGraphData(null);
    }
}

function sendPresetQuery(queryText) {
    const input = document.getElementById("chat-user-input");
    if (input) {
        input.value = queryText;
        document.getElementById("chat-input-form").dispatchEvent(new Event("submit"));
    }
}

async function handleSendChatMessage(event) {
    if (event) event.preventDefault();
    const projectId = document.getElementById("intelligence-project-select").value;
    if (!projectId) {
        alert("Please select an active project before sending queries.");
        return;
    }

    const input = document.getElementById("chat-user-input");
    const query = input.value.trim();
    if (!query) return;

    // 1. Add user message bubble
    appendChatMessage('user', query);
    input.value = "";

    // 2. Show thinking loader
    const loader = document.getElementById("chat-thinking-indicator");
    if (loader) loader.classList.remove("hidden-element");

    const chatContainer = document.getElementById("chat-messages-container");
    if (chatContainer) chatContainer.scrollTop = chatContainer.scrollHeight;

    try {
        const res = await apiCall(`/api/projects/${projectId}/query`, 'POST', { query });
        appendChatMessage('ai', res.answer, res.referencedEntityIds);

        // Update graph highlights dynamically
        graphState.aiHighlightedNodes.clear();
        if (res.referencedEntityIds && res.referencedEntityIds.length > 0) {
            // Expand all parents to ensure highlighted nodes are fully visible
            graphState.collapsedNodes.clear();
            res.referencedEntityIds.forEach(refStr => {
                graphState.aiHighlightedNodes.add(refStr);
            });
            applyGraphFilters();
        }
    } catch (err) {
        console.error("AI Query failed", err);
        appendChatMessage('ai', "I apologize, but I failed to communicate with the semantic backend. Please verify your connection status.");
    } finally {
        if (loader) loader.classList.add("hidden-element");
        if (chatContainer) chatContainer.scrollTop = chatContainer.scrollHeight;
    }
}

function appendChatMessage(sender, text, references = []) {
    const container = document.getElementById("chat-messages-container");
    if (!container) return;

    const msgWrapper = document.createElement("div");
    msgWrapper.className = sender === 'user' ? 'chat-user-message' : 'chat-ai-message';

    const avatar = document.createElement("span");
    avatar.className = sender === 'user' ? 'user-avatar' : 'ai-avatar';
    avatar.innerText = sender === 'user' ? '👤' : '🧠';

    const bubble = document.createElement("div");
    bubble.className = "message-bubble";

    // Simple markdown formatting helper
    bubble.innerHTML = formatMarkdown(text);

    // If there are clickable entity references, build chip links
    if (references && references.length > 0) {
        const chipsContainer = document.createElement("div");
        chipsContainer.className = "referenced-entities-container";
        chipsContainer.innerHTML = `<div class="referenced-title">Related Items:</div>`;

        references.forEach(refStr => {
            const splitIndex = refStr.indexOf("_");
            if (splitIndex === -1) return;
            const type = refStr.substring(0, splitIndex).toUpperCase();
            const id = refStr.substring(splitIndex + 1);

            const chip = document.createElement("div");
            chip.className = `entity-chip entity-chip-${type.toLowerCase()}`;
            chip.innerText = `${getIconForType(type)} ${getLabelForType(type, id)}`;
            chip.onclick = () => navigateToEntity(type, id);

            chipsContainer.appendChild(chip);
        });

        bubble.appendChild(chipsContainer);
    }

    msgWrapper.appendChild(avatar);
    msgWrapper.appendChild(bubble);
    container.appendChild(msgWrapper);

    container.scrollTop = container.scrollHeight;
}

function formatMarkdown(text) {
    if (!text) return "";
    let html = escapeHtml(text);

    // Bold text **bold**
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Code blocks `code`
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Convert newlines to paragraphs/breaks
    html = html.split('\n\n').map(p => `<p>${p.replace(/\n/g, '<br>')}</p>`).join('');

    return html;
}

function getIconForType(type) {
    switch (type) {
        case 'PROJECT': return '📁';
        case 'EPIC': return '⚡';
        case 'FEATURE': return '⚡';
        case 'REQUIREMENT': return '📌';
        case 'TASK': return '⚙';
        case 'CLASS': return '🟥';
        case 'METHOD': return '🟩';
        default: return '🔗';
    }
}

function getLabelForType(type, id) {
    // Attempt to resolve friendly name from active cached workspace variables
    if (type === 'REQUIREMENT') {
        const f = state.activeExplorerFeatures || [];
        for (let feat of f) {
            const r = (feat.requirements || []).find(req => req.id === id);
            if (r) return r.title;
        }
    } else if (type === 'TASK') {
        const f = state.activeExplorerFeatures || [];
        for (let feat of f) {
            const t = (feat.tasks || []).find(task => task.id === id);
            if (t) return t.title;
        }
    }
    // Return capitalized type and truncated ID
    return `${type} (${id.substring(0, 5)})`;
}

function navigateToEntity(type, id) {
    if (type === 'PROJECT') {
        loadProjectDetails(id);
        switchTab('projects');
    } else {
        // Focus and select node on the integrated graph explorer canvas in-place!
        const nodeId = `${type}_${id}`;
        
        // Ensure all parent branches are expanded
        graphState.collapsedNodes.clear();
        applyGraphFilters();

        const node = graphState.visibleNodes.find(n => n.id === nodeId);
        if (node) {
            graphState.selectedNode = node;
            calculateNodeRelations(node);
            showGraphNodeDetails(node);
        }
    }
}

function initWorkspaceSplitter() {
    const container = document.querySelector(".intelligence-workspace-container");
    const leftCol = document.querySelector(".intelligence-graph-column");
    const rightCol = document.querySelector(".chat-main-column");
    const splitter = document.getElementById("intelligence-splitter");
    if (!container || !leftCol || !rightCol || !splitter) return;

    let isDragging = false;

    splitter.addEventListener("mousedown", (e) => {
        isDragging = true;
        splitter.classList.add("active");
        document.body.style.cursor = "col-resize";
        document.body.style.userSelect = "none";
        e.preventDefault();
    });

    document.addEventListener("mousemove", (e) => {
        if (!isDragging) return;
        
        const containerRect = container.getBoundingClientRect();
        const offsetLeft = e.clientX - containerRect.left;
        
        let leftPercent = (offsetLeft / containerRect.width) * 100;
        if (leftPercent < 15) leftPercent = 15;
        if (leftPercent > 85) leftPercent = 85;
        
        leftCol.style.flex = `0 0 ${leftPercent}%`;
        rightCol.style.flex = `0 0 ${100 - leftPercent - 0.5}%`;
        
        // Trigger canvas resize loops
        const canvas = document.getElementById("graph-canvas");
        if (canvas) {
            const parent = canvas.parentElement;
            if (parent) {
                canvas.width = parent.clientWidth;
                canvas.height = parent.clientHeight;
            }
        }
    });

    document.addEventListener("mouseup", () => {
        if (isDragging) {
            isDragging = false;
            splitter.classList.remove("active");
            document.body.style.cursor = "";
            document.body.style.userSelect = "";
        }
    });
}

// GitHub Workspace Logic
function switchProjectSubTab(tabName) {
    const planningTab = document.getElementById("project-tab-planning");
    const githubTab = document.getElementById("project-tab-github");
    const planningContent = document.getElementById("project-planning-content");
    const githubContent = document.getElementById("project-github-content");
    if (!planningTab || !githubTab || !planningContent || !githubContent) return;

    if (tabName === 'planning') {
        planningTab.classList.add("active-tab");
        githubTab.classList.remove("active-tab");
        planningContent.style.display = "grid";
        githubContent.style.display = "none";
    } else if (tabName === 'github') {
        planningTab.classList.remove("active-tab");
        githubTab.classList.add("active-tab");
        planningContent.style.display = "none";
        githubContent.style.display = "grid";
        
        loadGitHubWorkspace();
    }
}

async function loadGitHubWorkspace() {
    const projectId = state.activeProjectId;
    if (!projectId) return;

    const statusInd = document.getElementById("github-pat-status-indicator");
    const connectForm = document.getElementById("github-connect-form-container");
    const connectedActions = document.getElementById("github-connected-actions");
    const dashboardUnlinked = document.getElementById("github-dashboard-unlinked");
    const dashboardLinked = document.getElementById("github-dashboard-linked");
    if (!statusInd || !connectForm || !connectedActions || !dashboardUnlinked || !dashboardLinked) return;

    // Fetch active project status
    const project = state.activeExplorerProject;
    
    // Check PAT connection status
    statusInd.innerHTML = "<span style='color: var(--text-secondary);'>Checking GitHub authorization status...</span>";
    let tokenStatus;
    try {
        tokenStatus = await apiCall("/api/github/status");
    } catch (e) {
        tokenStatus = { connected: false, error: e.message };
    }

    if (!tokenStatus.connected) {
        statusInd.innerHTML = `
            <div style="padding: 12px; border-radius: 6px; background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.2); color: #f87171;">
                <strong>GitHub Connection Inactive:</strong><br>
                Please set the <code>GITHUB_PAT</code> environment variable with standard <code>repo</code> access scopes.
            </div>`;
        connectForm.classList.add("hidden-element");
        connectedActions.classList.add("hidden-element");
        dashboardUnlinked.classList.remove("hidden-element");
        dashboardLinked.classList.add("hidden-element");
        return;
    }

    statusInd.innerHTML = `
        <div class="github-badge">
            <span>🐙 Connected as: <strong>${tokenStatus.username}</strong></span>
        </div>`;

    if (project.githubRepoName && project.githubOwner) {
        // Linked repository
        connectForm.classList.add("hidden-element");
        connectedActions.classList.remove("hidden-element");
        dashboardUnlinked.classList.add("hidden-element");
        dashboardLinked.classList.remove("hidden-element");

        loadGitHubDashboardData(projectId);
    } else {
        // Not linked repository
        connectForm.classList.remove("hidden-element");
        connectedActions.classList.add("hidden-element");
        dashboardUnlinked.classList.remove("hidden-element");
        dashboardLinked.classList.add("hidden-element");

        // Load repositories for select dropdown
        const repoSelect = document.getElementById("github-repo-select");
        if (repoSelect) {
            repoSelect.innerHTML = "<option value=''>Loading repositories...</option>";
            try {
                const repos = await apiCall("/api/github/repos");
                repoSelect.innerHTML = "<option value=''>-- Select Existing Repo --</option>";
                repos.forEach(r => {
                    repoSelect.innerHTML += `<option value="${r.owner.login}/${r.name}">${r.full_name} (${r.private ? 'Private' : 'Public'})</option>`;
                });
            } catch (err) {
                repoSelect.innerHTML = "<option value=''>Error loading repositories</option>";
            }
        }
    }
}

async function handleGitHubConnect() {
    const projectId = state.activeProjectId;
    if (!projectId) return;

    const repoSelect = document.getElementById("github-repo-select");
    const newRepoInput = document.getElementById("github-new-repo-name");
    if (!repoSelect || !newRepoInput) return;

    const selectedRepo = repoSelect.value;
    const newRepoName = newRepoInput.value.trim();

    if (!selectedRepo && !newRepoName) {
        alert("Please select an existing repository or enter a name for a new one.");
        return;
    }

    let payload = {};
    if (newRepoName) {
        payload = { repoName: newRepoName, createRepo: true };
    } else {
        const [owner, repoName] = selectedRepo.split("/");
        payload = { repoName: repoName, owner: owner, createRepo: false };
    }

    try {
        const updated = await apiCall(`/api/projects/${projectId}/github/connect`, "POST", payload);
        state.activeExplorerProject = updated;
        // Force refresh explorer sidebar summary if needed
        renderProjectExplorerSummary();
        loadGitHubWorkspace();
        newRepoInput.value = "";
    } catch (err) {
        alert("Failed to connect repository: " + err.message);
    }
}

async function loadGitHubDashboardData(projectId) {
    const commitsContainer = document.getElementById("github-commits-list");
    const branchesContainer = document.getElementById("github-branches-list");
    const pullsContainer = document.getElementById("github-pulls-list");
    const prCompareSelect = document.getElementById("github-pr-head");
    
    if (commitsContainer) commitsContainer.innerHTML = "<div class='muted-text'>Loading commits...</div>";
    if (branchesContainer) branchesContainer.innerHTML = "<div class='muted-text'>Loading branches...</div>";
    if (pullsContainer) pullsContainer.innerHTML = "<div class='muted-text'>Loading PRs...</div>";
    if (prCompareSelect) prCompareSelect.innerHTML = "<option value=''>Loading branches...</option>";

    // Fetch commits
    apiCall(`/api/projects/${projectId}/github/commits`).then(commits => {
        if (!commitsContainer) return;
        if (commits.length === 0) {
            commitsContainer.innerHTML = "<div class='muted-text'>No commits found in repository.</div>";
            return;
        }
        commitsContainer.innerHTML = "";
        commits.forEach(c => {
            const commit = c.commit;
            const author = commit.author ? commit.author.name : "Unknown";
            const dateStr = commit.author ? new Date(commit.author.date).toLocaleDateString() : "";
            commitsContainer.innerHTML += `
                <div class="github-item">
                    <span class="github-commit-msg">${commit.message}</span>
                    <span class="github-commit-author">by ${author} on ${dateStr}</span>
                </div>`;
        });
    }).catch(err => {
        if (commitsContainer) commitsContainer.innerHTML = `<div class='muted-text' style='color: #f87171;'>Failed to load commits: ${err.message}</div>`;
    });

    // Fetch branches
    apiCall(`/api/projects/${projectId}/github/branches`).then(branches => {
        if (prCompareSelect) prCompareSelect.innerHTML = "<option value=''>Compare Branch</option>";
        if (!branchesContainer) return;
        if (branches.length === 0) {
            branchesContainer.innerHTML = "<div class='muted-text'>No branches found.</div>";
            return;
        }
        branchesContainer.innerHTML = "";
        branches.forEach(b => {
            branchesContainer.innerHTML += `
                <div class="github-branch-item">
                    <span class="github-branch-name">⎇ ${b.name}</span>
                    <span class="github-pr-meta">${b.protected ? '🔒 Protected' : ''}</span>
                </div>`;
            if (prCompareSelect) {
                prCompareSelect.innerHTML += `<option value="${b.name}">${b.name}</option>`;
            }
        });
    }).catch(err => {
        if (branchesContainer) branchesContainer.innerHTML = `<div class='muted-text' style='color: #f87171;'>Failed to load branches: ${err.message}</div>`;
    });

    // Fetch Pull Requests
    apiCall(`/api/projects/${projectId}/github/pulls`).then(pulls => {
        if (!pullsContainer) return;
        if (pulls.length === 0) {
            pullsContainer.innerHTML = "<div class='muted-text'>No Pull Requests found.</div>";
            return;
        }
        pullsContainer.innerHTML = "";
        pulls.forEach(p => {
            let statusClass = "pr-open";
            let statusText = p.state;
            if (p.merged_at) {
                statusClass = "pr-merged";
                statusText = "merged";
            } else if (p.state === 'closed') {
                statusClass = "pr-closed";
            }

            pullsContainer.innerHTML += `
                <div class="github-item">
                    <div class="github-item-header">
                        <strong style="color: var(--text-primary);"><a href="${p.html_url}" target="_blank" style="color: inherit; text-decoration: underline;">#${p.number} ${p.title}</a></strong>
                        <span class="github-pr-status ${statusClass}">${statusText}</span>
                    </div>
                    <span class="github-pr-meta">Compare: <code>${p.head.ref}</code> ➔ <code>${p.base.ref}</code></span>
                </div>`;
        });
    }).catch(err => {
        if (pullsContainer) pullsContainer.innerHTML = `<div class='muted-text' style='color: #f87171;'>Failed to load pull requests: ${err.message}</div>`;
    });
}

async function handleCreateBranch(event) {
    event.preventDefault();
    const projectId = state.activeProjectId;
    if (!projectId) return;

    const branchInput = document.getElementById("github-branch-input");
    if (!branchInput) return;

    const branchName = branchInput.value.trim();
    if (!branchName) return;

    try {
        await apiCall(`/api/projects/${projectId}/github/branches`, "POST", { branchName: branchName });
        loadGitHubDashboardData(projectId);
        branchInput.value = "";
        alert(`Branch '${branchName}' created successfully.`);
    } catch (err) {
        alert("Failed to create branch: " + err.message);
    }
}

async function handleCreatePR(event) {
    event.preventDefault();
    const projectId = state.activeProjectId;
    if (!projectId) return;

    const titleInput = document.getElementById("github-pr-title");
    const headSelect = document.getElementById("github-pr-head");
    const baseInput = document.getElementById("github-pr-base");
    const bodyInput = document.getElementById("github-pr-body");
    if (!titleInput || !headSelect || !baseInput || !bodyInput) return;

    const payload = {
        title: titleInput.value.trim(),
        head: headSelect.value,
        base: baseInput.value.trim(),
        body: bodyInput.value.trim()
    };

    if (!payload.title || !payload.head || !payload.base) {
        alert("Please fill in all required PR fields.");
        return;
    }

    try {
        await apiCall(`/api/projects/${projectId}/github/pulls`, "POST", payload);
        loadGitHubDashboardData(projectId);
        titleInput.value = "";
        bodyInput.value = "";
        alert("Pull request created successfully.");
    } catch (err) {
        alert("Failed to create pull request: " + err.message);
    }
}




