// Global State
let logs = [];
let autoScroll = true;
let eventSource = null;
let botStateEventSource = null;
let profiles = [];
let currentView = 'logs';

// Pagination State
let currentPage = 1;
let pageSize = parseInt(localStorage.getItem('logPageSize')) || 50;

// Navigation Elements
const hamburgerIcon = document.getElementById('hamburgerIcon');
const sideNav = document.getElementById('sideNav');
const mainContent = document.getElementById('mainContent');
const navItems = document.querySelectorAll('.nav-item');

// Logs View Elements
const logsTableBody = document.getElementById('logsTableBody');
const logContainer = document.getElementById('logContainer');
const statusIndicator = document.getElementById('statusIndicator');
const statusText = document.getElementById('statusText');
const searchLogs = document.getElementById('searchLogs');
const filterProfile = document.getElementById('filterProfile');
const filterSeverity = document.getElementById('filterSeverity');
const debugMode = document.getElementById('debugMode');
const logCount = document.getElementById('logCount');
const clearLogsBtn = document.getElementById('clearLogs');
const refreshLogsBtn = document.getElementById('refreshLogs');

// Pagination Elements
const pageSizeSelect = document.getElementById('pageSize');
const prevPageBtn = document.getElementById('prevPage');
const nextPageBtn = document.getElementById('nextPage');
const pageInfo = document.getElementById('pageInfo');

// Profiles View Elements
const profilesGrid = document.getElementById('profilesGrid');

// Tasks View Elements
const tasksContainer = document.getElementById('tasksContainer');
const taskProfileFilter = document.getElementById('taskProfileFilter');

// ==================== Navigation ====================

function toggleMenu() {
    hamburgerIcon.classList.toggle('active');
    sideNav.classList.toggle('open');
    mainContent.classList.toggle('shifted');
}

function switchView(viewName) {
    // Remove active class from all views and nav items
    document.querySelectorAll('.view').forEach(view => view.classList.remove('active'));
    navItems.forEach(item => item.classList.remove('active'));
    
    // Add active class to selected view and nav item
    document.getElementById(`${viewName}View`).classList.add('active');
    document.querySelector(`[data-view="${viewName}"]`).classList.add('active');
    
    currentView = viewName;
    
    // Load data for the view
    if (viewName === 'profiles') {
        loadProfiles();
    } else if (viewName === 'tasks') {
        loadTasks();
    }
    
    // Close menu on mobile
    if (window.innerWidth <= 768) {
        toggleMenu();
    }
}

// Event Listeners for Navigation
hamburgerIcon.addEventListener('click', toggleMenu);

navItems.forEach(item => {
    item.addEventListener('click', () => {
        const viewName = item.getAttribute('data-view');
        switchView(viewName);
    });
});

// Close menu when clicking outside
document.addEventListener('click', (event) => {
    const isClickInsideMenu = sideNav.contains(event.target);
    const isClickOnHamburger = hamburgerIcon.contains(event.target);
    
    if (!isClickInsideMenu && !isClickOnHamburger && sideNav.classList.contains('open')) {
        toggleMenu();
    }
});

// ==================== Logs View ====================

function connectToLogStream() {
    eventSource = new EventSource('/logs/stream');
    
    eventSource.addEventListener('log', (event) => {
        const log = JSON.parse(event.data);
        logs.push(log);
        populateProfileFilter();
        renderLogs();
    });
    
    eventSource.onopen = () => {
        statusIndicator.classList.remove('disconnected');
        statusText.textContent = 'Connected';
    };
    
    eventSource.onerror = () => {
        statusIndicator.classList.add('disconnected');
        statusText.textContent = 'Disconnected - Reconnecting...';
        eventSource.close();
        setTimeout(connectToLogStream, 3000);
    };
}

function connectToBotStateStream() {
    botStateEventSource = new EventSource('/api/bot/state/stream');
    
    botStateEventSource.addEventListener('botState', (event) => {
        const botState = JSON.parse(event.data);
        console.log('Received bot state update:', botState);
        
        // Determine status from botState
        let status;
        if (!botState.running) {
            status = 'stopped';
        } else if (botState.paused) {
            status = 'paused';
        } else {
            status = 'running';
        }
        
        updateBotStatus(status);
    });
    
    botStateEventSource.onopen = () => {
        console.log('Connected to bot state stream');
    };
    
    botStateEventSource.onerror = () => {
        console.log('Bot state stream disconnected - Reconnecting...');
        botStateEventSource.close();
        setTimeout(connectToBotStateStream, 3000);
    };
}

function formatTimestamp(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${month}/${day}/${year} ${hours}:${minutes}:${seconds}`;
}

function renderLogs() {
    const searchText = searchLogs.value.toLowerCase();
    const profileFilter = filterProfile.value;
    const severityFilter = filterSeverity.value;
    const showDebug = debugMode.checked;
    
    const filteredLogs = logs.filter(log => {
        // Filter by debug mode
        if (!showDebug && log.severity === 'DEBUG') return false;
        
        // Filter by profile dropdown
        if (profileFilter && log.profile !== profileFilter) return false;
        
        // Filter by severity dropdown
        if (severityFilter && log.severity !== severityFilter) return false;
        
        // Filter by search text (searches in all fields)
        if (searchText) {
            const searchableText = `${log.profile} ${log.task} ${log.message} ${log.severity}`.toLowerCase();
            if (!searchableText.includes(searchText)) return false;
        }
        
        return true;
    });
    
    // Reverse the logs so most recent appear first (at the top)
    const reversedLogs = filteredLogs.slice().reverse();
    
    // Calculate pagination
    const totalLogs = reversedLogs.length;
    const totalPages = Math.ceil(totalLogs / pageSize);
    
    // Ensure current page is within bounds
    if (currentPage > totalPages && totalPages > 0) {
        currentPage = totalPages;
    }
    if (currentPage < 1) {
        currentPage = 1;
    }
    
    // Calculate slice indices for current page
    const startIndex = (currentPage - 1) * pageSize;
    const endIndex = Math.min(startIndex + pageSize, totalLogs);
    const paginatedLogs = reversedLogs.slice(startIndex, endIndex);
    
    // Update log count
    const showingStart = totalLogs === 0 ? 0 : startIndex + 1;
    const showingEnd = endIndex;
    logCount.textContent = `Showing ${showingStart}-${showingEnd} of ${totalLogs} logs (${logs.length} total)`;
    
    // Update pagination UI
    pageInfo.textContent = `Page ${currentPage} of ${Math.max(1, totalPages)}`;
    prevPageBtn.disabled = currentPage <= 1;
    nextPageBtn.disabled = currentPage >= totalPages;
    
    if (paginatedLogs.length === 0) {
        logsTableBody.innerHTML = '<tr class="no-logs-row"><td colspan="5">No logs match the current filters.</td></tr>';
        return;
    }
    
    const html = paginatedLogs.map(log => `
        <tr>
            <td>${formatTimestamp(log.timestamp)}</td>
            <td><span class="log-level ${log.severity}">${log.severity}</span></td>
            <td>${escapeHtml(log.profile)}</td>
            <td>${escapeHtml(log.task)}</td>
            <td>${escapeHtml(log.message)}</td>
        </tr>
    `).join('');
    
    logsTableBody.innerHTML = html;
    
    // With reversed order, auto-scroll should go to top to see most recent
    if (autoScroll) {
        logContainer.scrollTop = 0;
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function populateProfileFilter() {
    const uniqueProfiles = [...new Set(logs.map(log => log.profile))];
    const currentValue = filterProfile.value;
    
    const options = uniqueProfiles.map(profile => 
        `<option value="${escapeHtml(profile)}">${escapeHtml(profile)}</option>`
    ).join('');
    
    filterProfile.innerHTML = '<option value="">All profiles</option>' + options;
    
    // Restore previous selection if it still exists
    if (currentValue && uniqueProfiles.includes(currentValue)) {
        filterProfile.value = currentValue;
    }
}

clearLogsBtn.addEventListener('click', () => {
    logs = [];
    currentPage = 1;
    renderLogs();
    populateProfileFilter();
});

refreshLogsBtn.addEventListener('click', () => {
    renderLogs();
});

searchLogs.addEventListener('input', () => {
    currentPage = 1; // Reset to first page when filtering
    renderLogs();
});
filterProfile.addEventListener('change', () => {
    currentPage = 1; // Reset to first page when filtering
    renderLogs();
});
filterSeverity.addEventListener('change', () => {
    currentPage = 1; // Reset to first page when filtering
    renderLogs();
});
debugMode.addEventListener('change', () => {
    currentPage = 1; // Reset to first page when filtering
    renderLogs();
});

// Pagination event listeners
pageSizeSelect.addEventListener('change', () => {
    pageSize = parseInt(pageSizeSelect.value);
    localStorage.setItem('logPageSize', pageSize);
    currentPage = 1; // Reset to first page when changing page size
    renderLogs();
});

prevPageBtn.addEventListener('click', () => {
    if (currentPage > 1) {
        currentPage--;
        renderLogs();
    }
});

nextPageBtn.addEventListener('click', () => {
    currentPage++;
    renderLogs();
});

// Initialize page size selector from localStorage
pageSizeSelect.value = pageSize;

// ==================== Profiles View ====================

async function loadProfiles() {
    try {
        profilesGrid.innerHTML = '<div class="loading">Loading profiles...</div>';
        
        const response = await fetch('/api/profiles');
        if (!response.ok) throw new Error('Failed to fetch profiles');
        
        profiles = await response.json();
        renderProfiles();
        
        // Populate task filter dropdown
        populateTaskProfileFilter();
    } catch (error) {
        console.error('Error loading profiles:', error);
        profilesGrid.innerHTML = '<div class="loading">Error loading profiles. Please try again.</div>';
    }
}

function renderProfiles() {
    if (profiles.length === 0) {
        profilesGrid.innerHTML = '<div class="loading">No profiles found.</div>';
        return;
    }
    
    const html = profiles.map(profile => `
        <div class="profile-card">
            <div class="profile-card-header">
                <div class="profile-name">${escapeHtml(profile.name || 'Unnamed')}</div>
                <div class="profile-status ${profile.status ? '' : 'inactive'}">
                    ${profile.status || 'Inactive'}
                </div>
            </div>
            <div class="profile-info">
                <div class="profile-info-item">
                    <span class="profile-info-label">ID:</span>
                    <span class="profile-info-value">${profile.id}</span>
                </div>
                <div class="profile-info-item">
                    <span class="profile-info-label">Emulator:</span>
                    <span class="profile-info-value">#${profile.emulatorNumber || 'N/A'}</span>
                </div>
                <div class="profile-info-item">
                    <span class="profile-info-label">Server:</span>
                    <span class="profile-info-value">${profile.server || 'N/A'}</span>
                </div>
                <div class="profile-info-item">
                    <span class="profile-info-label">State:</span>
                    <span class="profile-info-value">${profile.state || 'Unknown'}</span>
                </div>
            </div>
        </div>
    `).join('');
    
    profilesGrid.innerHTML = html;
}

// ==================== Tasks View ====================

async function loadTasks() {
    try {
        tasksContainer.innerHTML = '<div class="loading">Loading tasks...</div>';
        
        const response = await fetch('/api/tasks');
        if (!response.ok) throw new Error('Failed to fetch tasks');
        
        const tasksData = await response.json();
        renderTasks(tasksData);
    } catch (error) {
        console.error('Error loading tasks:', error);
        tasksContainer.innerHTML = '<div class="loading">Error loading tasks. Please try again.</div>';
    }
}

function renderTasks(tasksData) {
    if (!tasksData || Object.keys(tasksData).length === 0) {
        tasksContainer.innerHTML = '<div class="loading">No tasks found.</div>';
        return;
    }
    
    const selectedProfileId = taskProfileFilter.value;
    
    let html = '';
    for (const [profileId, tasks] of Object.entries(tasksData)) {
        // Filter by selected profile if specified
        if (selectedProfileId && profileId !== selectedProfileId) {
            continue;
        }
        
        const profile = profiles.find(p => p.id.toString() === profileId);
        const profileName = profile ? profile.name : `Profile ${profileId}`;
        
        html += `
            <div class="profile-tasks-section">
                <div class="profile-tasks-header">
                    <div class="profile-tasks-name">${escapeHtml(profileName)}</div>
                </div>
                <div class="tasks-grid">
                    ${renderTaskCards(tasks)}
                </div>
            </div>
        `;
    }
    
    if (!html) {
        tasksContainer.innerHTML = '<div class="loading">No tasks match the selected profile.</div>';
        return;
    }
    
    tasksContainer.innerHTML = html;
}

function renderTaskCards(tasks) {
    if (!tasks || tasks.length === 0) {
        return '<div class="loading">No tasks for this profile.</div>';
    }
    
    return tasks.map(task => {
        const status = task.executing ? 'executing' : (task.scheduled ? 'scheduled' : 'disabled');
        const statusText = task.executing ? 'Executing' : (task.scheduled ? 'Scheduled' : 'Disabled');
        
        return `
            <div class="task-card ${status}">
                <div class="task-name">${escapeHtml(task.taskName || 'Unknown Task')}</div>
                <div class="task-details">
                    <div class="task-detail">
                        <span class="task-detail-label">Status:</span>
                        <span class="task-status-badge ${status}">${statusText}</span>
                    </div>
                    ${task.lastExecutionTime ? `
                        <div class="task-detail">
                            <span class="task-detail-label">Last Run:</span>
                            <span class="task-detail-value">${formatDateTime(task.lastExecutionTime)}</span>
                        </div>
                    ` : ''}
                    ${task.nextExecutionTime ? `
                        <div class="task-detail">
                            <span class="task-detail-label">Next Run:</span>
                            <span class="task-detail-value">${formatDateTime(task.nextExecutionTime)}</span>
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');
}

function populateTaskProfileFilter() {
    const options = profiles.map(profile => 
        `<option value="${profile.id}">${escapeHtml(profile.name || 'Unnamed')}</option>`
    ).join('');
    
    taskProfileFilter.innerHTML = '<option value="">All Profiles</option>' + options;
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return 'N/A';
    try {
        const date = new Date(dateTimeStr);
        return date.toLocaleString('en-US', { 
            month: 'short', 
            day: 'numeric', 
            hour: '2-digit', 
            minute: '2-digit' 
        });
    } catch {
        return dateTimeStr;
    }
}

// Event Listeners for Tasks View
taskProfileFilter.addEventListener('change', loadTasks);

// ==================== Bottom Bar / Bot Control ====================

// Bottom Bar Elements
const botStatusIndicator = document.getElementById('botStatusIndicator');
const botStatusText = document.getElementById('botStatusText');
const btnPause = document.getElementById('btnPause');
const btnResume = document.getElementById('btnResume');
const btnStart = document.getElementById('btnStart');
const btnStop = document.getElementById('btnStop');

// Bot status state
let botStatus = 'stopped'; // 'stopped', 'running', 'paused'

function updateBotStatus(status) {
    botStatus = status;
    
    // Update indicator
    botStatusIndicator.className = 'bot-status-indicator ' + status;
    
    // Update text
    const statusTexts = {
        'stopped': 'Bot Status: Stopped',
        'running': 'Bot Status: Running',
        'paused': 'Bot Status: Paused'
    };
    botStatusText.textContent = statusTexts[status] || 'Bot Status: Unknown';
    
    // Update button visibility
    if (status === 'stopped') {
        btnPause.style.display = 'none';
        btnResume.style.display = 'none';
        btnStart.style.display = 'flex';
        btnStop.style.display = 'none';
    } else if (status === 'running') {
        btnPause.style.display = 'flex';
        btnResume.style.display = 'none';
        btnStart.style.display = 'none';
        btnStop.style.display = 'flex';
    } else if (status === 'paused') {
        btnPause.style.display = 'none';
        btnResume.style.display = 'flex';
        btnStart.style.display = 'none';
        btnStop.style.display = 'flex';
    }
}

// Button event handlers
btnStart.addEventListener('click', async () => {
    console.log('Start Bot clicked');
    try {
        const response = await fetch('/api/bot/start', { method: 'POST' });
        const data = await response.json();
        if (!data.success) {
            console.error('Failed to start bot:', data.error);
            alert('Failed to start bot: ' + (data.error || 'Unknown error'));
        }
        // Status update will come from SSE stream
    } catch (error) {
        console.error('Error starting bot:', error);
        alert('Error starting bot. Check console for details.');
    }
});

btnStop.addEventListener('click', async () => {
    console.log('Stop Bot clicked');
    try {
        const response = await fetch('/api/bot/stop', { method: 'POST' });
        const data = await response.json();
        if (!data.success) {
            console.error('Failed to stop bot:', data.error);
            alert('Failed to stop bot: ' + (data.error || 'Unknown error'));
        }
        // Status update will come from SSE stream
    } catch (error) {
        console.error('Error stopping bot:', error);
        alert('Error stopping bot. Check console for details.');
    }
});

btnPause.addEventListener('click', async () => {
    console.log('Pause clicked');
    try {
        const response = await fetch('/api/bot/pause', { method: 'POST' });
        const data = await response.json();
        if (!data.success) {
            console.error('Failed to pause bot:', data.error);
            alert('Failed to pause bot: ' + (data.error || 'Unknown error'));
        }
        // Status update will come from SSE stream
    } catch (error) {
        console.error('Error pausing bot:', error);
        alert('Error pausing bot. Check console for details.');
    }
});

btnResume.addEventListener('click', async () => {
    console.log('Resume clicked');
    try {
        const response = await fetch('/api/bot/resume', { method: 'POST' });
        const data = await response.json();
        if (!data.success) {
            console.error('Failed to resume bot:', data.error);
            alert('Failed to resume bot: ' + (data.error || 'Unknown error'));
        }
        // Status update will come from SSE stream
    } catch (error) {
        console.error('Error resuming bot:', error);
        alert('Error resuming bot. Check console for details.');
    }
});

// ==================== Version Management ====================

async function loadVersion() {
    try {
        const response = await fetch('/api/version');
        if (!response.ok) throw new Error('Failed to fetch version');
        
        const data = await response.json();
        const versionElement = document.querySelector('.app-version');
        if (versionElement && data.version) {
            versionElement.textContent = `Whiteout Survival Bot v${data.version}`;
        }
    } catch (error) {
        console.error('Error loading version:', error);
        // Keep the default hardcoded version if fetch fails
    }
}

// ==================== Initialization ====================

// Start log connection
connectToLogStream();

// Start bot state connection
connectToBotStateStream();

// Load initial data for profiles (used by task filter)
loadProfiles();

// Load application version
loadVersion();
