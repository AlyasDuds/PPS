/**
 * Storage Monitor
 * 
 * Provides real-time storage monitoring functionality for administrators.
 * Displays disk usage, upload directory size, and storage status indicators.
 */

class StorageMonitor {
    constructor() {
        this.storageEndpoint = '/api/storage/status';
        this.refreshInterval = 60000; // 1 minute
        this.isInitialized = false;
    }

    init() {
        if (this.isInitialized) return;
        
        // Only initialize for admin users
        if (!this.isAdminUser()) return;
        
        this.createStorageWidget();
        this.startMonitoring();
        this.isInitialized = true;
    }

    isAdminUser() {
        // Check if user has admin role (you may need to adjust this based on your auth system)
        return document.body.getAttribute('data-user-role') === 'ADMIN' || 
               document.querySelector('[sec\\:authorize*="ADMIN"]') !== null;
    }

    createStorageWidget() {
        // Create storage widget container
        const widget = document.createElement('div');
        widget.id = 'storage-monitor-widget';
        widget.className = 'storage-monitor-widget';
        widget.innerHTML = `
            <div class="storage-header">
                <i class="fas fa-hdd"></i>
                <span>Storage Status</span>
                <button class="btn btn-sm btn-outline-secondary" onclick="storageMonitor.refreshStorage()">
                    <i class="fas fa-sync-alt"></i>
                </button>
            </div>
            <div class="storage-content">
                <div class="storage-loading">
                    <i class="fas fa-spinner fa-spin"></i> Checking storage...
                </div>
            </div>
        `;

        // Add styles
        const style = document.createElement('style');
        style.textContent = `
            .storage-monitor-widget {
                background: white;
                border: 1px solid #dee2e6;
                border-radius: 8px;
                padding: 15px;
                margin: 10px 0;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            .storage-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 10px;
                font-weight: 600;
                color: #495057;
            }
            .storage-header i {
                margin-right: 8px;
                color: #007bff;
            }
            .storage-content {
                font-size: 14px;
            }
            .storage-loading {
                text-align: center;
                color: #6c757d;
                padding: 10px;
            }
            .storage-status {
                display: flex;
                align-items: center;
                margin-bottom: 8px;
            }
            .storage-status-indicator {
                width: 12px;
                height: 12px;
                border-radius: 50%;
                margin-right: 8px;
            }
            .storage-status-healthy { background-color: #28a745; }
            .storage-status-warning { background-color: #ffc107; }
            .storage-status-critical { background-color: #dc3545; }
            .storage-progress {
                width: 100%;
                height: 8px;
                background-color: #e9ecef;
                border-radius: 4px;
                overflow: hidden;
                margin: 5px 0;
            }
            .storage-progress-bar {
                height: 100%;
                transition: width 0.3s ease;
            }
            .storage-progress-healthy { background-color: #28a745; }
            .storage-progress-warning { background-color: #ffc107; }
            .storage-progress-critical { background-color: #dc3545; }
            .storage-details {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 10px;
                margin-top: 10px;
                font-size: 12px;
            }
            .storage-detail-item {
                display: flex;
                justify-content: space-between;
            }
            .storage-detail-label {
                color: #6c757d;
            }
            .storage-detail-value {
                font-weight: 600;
            }
        `;
        document.head.appendChild(style);

        // Insert widget into dashboard or sidebar
        this.insertWidget(widget);
    }

    insertWidget(widget) {
        // Try to insert into dashboard stats cards area first
        const statsRow = document.querySelector('.row.mb-3');
        if (statsRow) {
            const col = document.createElement('div');
            col.className = 'col-xl-2-4 col-lg-2-4 col-md-4 col-sm-6 mb-2';
            col.appendChild(widget);
            statsRow.appendChild(col);
        } else {
            // Fallback: insert into sidebar
            const sidebar = document.querySelector('.sidebar-content');
            if (sidebar) {
                sidebar.appendChild(widget);
            }
        }
    }

    startMonitoring() {
        this.refreshStorage();
        setInterval(() => this.refreshStorage(), this.refreshInterval);
    }

    async refreshStorage() {
        const content = document.querySelector('.storage-content');
        if (!content) return;

        try {
            const response = await fetch(this.storageEndpoint);
            const data = await response.json();

            if (data.success) {
                this.displayStorageStatus(data);
            } else {
                this.displayError(data.message || 'Failed to load storage information');
            }
        } catch (error) {
            this.displayError('Network error while checking storage');
        }
    }

    displayStorageStatus(data) {
        const content = document.querySelector('.storage-content');
        if (!content) return;

        const status = data.status;
        const diskUsage = data.diskUsage;
        const uploads = data.uploads;

        content.innerHTML = `
            <div class="storage-status">
                <div class="storage-status-indicator storage-status-${status}"></div>
                <span class="storage-status-text">Disk ${status.toUpperCase()}</span>
            </div>
            <div class="storage-progress">
                <div class="storage-progress-bar storage-progress-${status}" 
                     style="width: ${diskUsage.usagePercent}%"></div>
            </div>
            <div class="storage-details">
                <div class="storage-detail-item">
                    <span class="storage-detail-label">Disk Usage:</span>
                    <span class="storage-detail-value">${diskUsage.usagePercent}%</span>
                </div>
                <div class="storage-detail-item">
                    <span class="storage-detail-label">Free Space:</span>
                    <span class="storage-detail-value">${diskUsage.freeGB} GB</span>
                </div>
                <div class="storage-detail-item">
                    <span class="storage-detail-label">Total Space:</span>
                    <span class="storage-detail-value">${diskUsage.totalGB} GB</span>
                </div>
                <div class="storage-detail-item">
                    <span class="storage-detail-label">Uploads Size:</span>
                    <span class="storage-detail-value">${uploads.sizeMB} MB</span>
                </div>
            </div>
        `;
    }

    displayError(message) {
        const content = document.querySelector('.storage-content');
        if (!content) return;

        content.innerHTML = `
            <div class="storage-error" style="color: #dc3545; text-align: center; padding: 10px;">
                <i class="fas fa-exclamation-triangle"></i>
                <div>${message}</div>
            </div>
        `;
    }
}

// Initialize storage monitor when DOM is ready
const storageMonitor = new StorageMonitor();

document.addEventListener('DOMContentLoaded', function() {
    // Delay initialization to ensure other components are loaded
    setTimeout(() => {
        storageMonitor.init();
    }, 1000);
});

// Global functions for sidebar integration
window.showStorageMonitor = function() {
    const modal = document.getElementById('storageMonitorModal');
    if (modal) {
        // Show modal
        if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            const bsModal = new bootstrap.Modal(modal);
            bsModal.show();
        } else if (typeof $.fn.modal === 'function') {
            $(modal).modal('show');
        }
        
        // Load storage data
        loadStorageMonitorModal();
    }
};

window.refreshStorageMonitor = function() {
    loadStorageMonitorModal();
};

function loadStorageMonitorModal() {
    const content = document.getElementById('storageMonitorContent');
    if (!content) return;

    content.innerHTML = `
        <div class="text-center py-4">
            <i class="fas fa-spinner fa-spin fa-2x mb-3 text-muted"></i>
            <p class="text-muted">Loading storage information...</p>
        </div>
    `;

    fetch('/api/storage/status')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                displayStorageModal(data);
                updateSidebarBadge(data.status);
            } else {
                displayStorageError(data.message || 'Failed to load storage information');
            }
        })
        .catch(error => {
            displayStorageError('Network error while checking storage');
        });
}

function displayStorageModal(data) {
    const content = document.getElementById('storageMonitorContent');
    if (!content) return;

    const status = data.status;
    const diskUsage = data.diskUsage;
    const uploads = data.uploads;

    const statusColor = {
        'healthy': '#28a745',
        'warning': '#ffc107', 
        'critical': '#dc3545'
    }[status] || '#6c757d';

    content.innerHTML = `
        <div class="row">
            <div class="col-md-6">
                <div class="card">
                    <div class="card-header">
                        <h6 class="mb-0">
                            <i class="fas fa-hdd mr-2"></i>Disk Usage
                        </h6>
                    </div>
                    <div class="card-body">
                        <div class="mb-3">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="font-weight-bold">Status:</span>
                                <span class="badge" style="background-color: ${statusColor}; color: white;">
                                    ${status.toUpperCase()}
                                </span>
                            </div>
                            <div class="progress mb-2" style="height: 20px;">
                                <div class="progress-bar" style="width: ${diskUsage.usagePercent}%; background-color: ${statusColor};">
                                    ${diskUsage.usagePercent}%
                                </div>
                            </div>
                        </div>
                        <div class="storage-details">
                            <div class="d-flex justify-content-between mb-2">
                                <span>Total Space:</span>
                                <strong>${diskUsage.totalGB} GB</strong>
                            </div>
                            <div class="d-flex justify-content-between mb-2">
                                <span>Used Space:</span>
                                <strong>${diskUsage.usedGB} GB</strong>
                            </div>
                            <div class="d-flex justify-content-between">
                                <span>Free Space:</span>
                                <strong class="text-success">${diskUsage.freeGB} GB</strong>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="col-md-6">
                <div class="card">
                    <div class="card-header">
                        <h6 class="mb-0">
                            <i class="fas fa-upload mr-2"></i>Upload Directory
                        </h6>
                    </div>
                    <div class="card-body">
                        <div class="text-center py-3">
                            <i class="fas fa-folder fa-3x mb-3 text-primary"></i>
                            <h4>${uploads.sizeMB} MB</h4>
                            <p class="text-muted mb-0">${uploads.path}</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div class="mt-3">
            <div class="alert alert-info">
                <i class="fas fa-info-circle mr-2"></i>
                <strong>Storage Recommendations:</strong>
                <ul class="mb-0 mt-2">
                    <li>Keep at least 20% free disk space for optimal performance</li>
                    <li>Consider archiving old data when usage exceeds 80%</li>
                    <li>Monitor upload directory size regularly</li>
                </ul>
            </div>
        </div>
    `;
}

function displayStorageError(message) {
    const content = document.getElementById('storageMonitorContent');
    if (!content) return;

    content.innerHTML = `
        <div class="alert alert-danger">
            <i class="fas fa-exclamation-triangle mr-2"></i>
            <strong>Error:</strong> ${message}
        </div>
    `;
}

function updateSidebarBadge(status) {
    const badge = document.getElementById('storageStatusBadge');
    if (!badge) return;

    const statusConfig = {
        'healthy': { color: '#28a745', text: 'OK' },
        'warning': { color: '#ffc107', text: 'WARN' },
        'critical': { color: '#dc3545', text: 'CRIT' }
    };

    const config = statusConfig[status] || statusConfig['healthy'];
    
    badge.style.backgroundColor = config.color;
    badge.textContent = config.text;
    badge.style.display = 'inline-block';
}

// Make it globally accessible
window.storageMonitor = storageMonitor;
