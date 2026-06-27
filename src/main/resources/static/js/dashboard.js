/**
 * Dashboard — Stats Cards + Filter Panel + System Admin Table
 *
 * System Admin columns: # | Name | Area | Province | City | Connection | Office | Actions
 * Area Admin columns: # | Name | Province | City | Connectivity | Office Status | Actions (Area hidden)
 * User columns: # | Name | Province | City | Connectivity | Office Status | Actions (Area hidden)
 *
 * Edit modal handled by edit-modal.js — do NOT bind .btn-edit here.
 */

let dashboardTable;
let map;
let markers = [];
let markerClusterGroup;

/** Role flags from #dashboardRoleFlags (layout has no role attrs on document.body). */
function dashboardRoleAttr(dashedKey) {
    const el = document.getElementById('dashboardRoleFlags');
    if (!el) return false;
    const v = el.getAttribute('data-' + dashedKey);
    return v != null && String(v).toLowerCase() === 'true';
}

// Full dashboard table (system admin + SRD layout): Thymeleaf isSystemAdmin = role 1 or 4
const IS_ADMIN = dashboardRoleAttr('is-system-admin') ||
    (document.getElementById('systemAdminTable')
        // Area column is now before Name (2nd column after #)
        ?.querySelector('thead th:nth-child(2)')
        ?.textContent?.trim().toLowerCase().includes('area') ?? false);

const IS_AREA_ADMIN = dashboardRoleAttr('is-area-admin');
const IS_PRIVILEGED_USER = IS_ADMIN || IS_AREA_ADMIN;

document.addEventListener('DOMContentLoaded', function () {

    // ── Initialize Stats Cards ─────────────────────────────────────────────
    initializeStatsCards();

    // ── Initialize Filter Panel ───────────────────────────────────────────
    initializeFilterPanel();

    // ── Initialize System Admin Table (if visible) ───────────────────────
    if (IS_ADMIN && document.getElementById('systemAdminTable')) {
        initializeSystemAdminTable();
    }

    // ── Initialize Regular Dashboard Table (if visible) ───────────────────
    if (!IS_ADMIN && document.getElementById('officeTable')) {
        initializeOfficeTable();
    }

    // ── Initialize Map ─────────────────────────────────────────────────────
    if (document.getElementById('map')) {
        initializeMap();
    }
});

// ── Stats Cards Functionality ───────────────────────────────────────────
// Total / Connected / Disconnected come from the server (same logic as Connectivity Report).
// Table search filters do not overwrite those quarterly snapshot cards.
function initializeStatsCards() {
    // Initialize WebSocket for real-time online users
    initializeWebSocket();
    
    // Update online users details (includes count) - initial load
    updateOnlineUsersDetails();

    // Initialize area carousel
    initializeAreaCarousel();
}

function initializeWebSocket() {
    const socket = new SockJS('/ws');
    const stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('Connected to WebSocket: ' + frame);
        
        // Subscribe to live updates
        stompClient.subscribe('/topic/online-users', function (message) {
            const data = JSON.parse(message.body);
            updateOnlineUsersFromWebSocket(data);
        });
    }, function (error) {
        console.log('WebSocket error: ' + error);
        // Fallback to polling if WebSocket fails
        setInterval(updateOnlineUsersDetails, 5000);
    });
}

function updateOnlineUsersFromWebSocket(data) {
    // Update count badge
    const onlineCountEl = document.getElementById('onlineUsersCount');
    if (onlineCountEl) {
        onlineCountEl.textContent = data.count;
    }
    
    // Update table
    displayOnlineUsersFromWebSocket(data.users);
}

function displayOnlineUsersFromWebSocket(onlineUsers) {
    const onlineUsersList = document.getElementById('onlineUsersList');
    if (!onlineUsersList) return;

    if (onlineUsers.length === 0) {
        onlineUsersList.innerHTML = '<tr><td colspan="4" class="text-center">No users online</td></tr>';
        return;
    }

    let html = '';
    onlineUsers.forEach(function(user) {
        html += `
            <tr>
                <td>${user.username || 'N/A'}</td>
                <td>${user.officeName || 'N/A'}</td>
                <td>${user.areaName || 'N/A'}</td>
                <td>${user.connectedAt || 'N/A'}</td>
            </tr>
        `;
    });

    onlineUsersList.innerHTML = html;
}

function initializeAreaCarousel() {
    const carousel = document.getElementById('areaCarousel');
    if (!carousel) return;

    let currentPosition = 0;
    const scrollAmount = 310; // 300px min-width + 10px padding
    const scrollInterval = 5000; // Scroll every 5 seconds
    let autoScrollInterval;

    // Auto-scroll function
    function autoScroll() {
        const containerWidth = carousel.parentElement.offsetWidth;
        const trackWidth = carousel.scrollWidth;

        currentPosition += scrollAmount;

        // Reset to start if we've scrolled past the end
        if (currentPosition >= trackWidth - containerWidth) {
            currentPosition = 0;
        }

        carousel.style.transform = `translateX(-${currentPosition}px)`;
    }

    // Start auto-scroll
    autoScrollInterval = setInterval(autoScroll, scrollInterval);

    // Drag functionality
    let isDragging = false;
    let startX;
    let scrollLeft;

    carousel.addEventListener('mousedown', (e) => {
        isDragging = true;
        startX = e.pageX - carousel.offsetLeft;
        scrollLeft = currentPosition;
        carousel.style.cursor = 'grabbing';
        carousel.style.transition = 'none'; // Disable transition during drag
        clearInterval(autoScrollInterval); // Pause auto-scroll during drag
    });

    carousel.addEventListener('mouseleave', () => {
        isDragging = false;
        carousel.style.cursor = 'grab';
        carousel.style.transition = 'transform 0.5s ease-in-out'; // Re-enable transition
        autoScrollInterval = setInterval(autoScroll, scrollInterval); // Resume auto-scroll
    });

    carousel.addEventListener('mouseup', () => {
        isDragging = false;
        carousel.style.cursor = 'grab';
        carousel.style.transition = 'transform 0.5s ease-in-out'; // Re-enable transition
        autoScrollInterval = setInterval(autoScroll, scrollInterval); // Resume auto-scroll
    });

    carousel.addEventListener('mousemove', (e) => {
        if (!isDragging) return;
        e.preventDefault();
        const x = e.pageX - carousel.offsetLeft;
        const walk = (x - startX);
        currentPosition = scrollLeft - walk;

        // Boundary checks
        const containerWidth = carousel.parentElement.offsetWidth;
        const trackWidth = carousel.scrollWidth;
        const maxPosition = trackWidth - containerWidth;

        if (currentPosition < 0) currentPosition = 0;
        if (currentPosition > maxPosition) currentPosition = maxPosition;

        carousel.style.transform = `translateX(-${currentPosition}px)`;
    });

    // Touch support for mobile
    carousel.addEventListener('touchstart', (e) => {
        isDragging = true;
        startX = e.touches[0].pageX - carousel.offsetLeft;
        scrollLeft = currentPosition;
        carousel.style.transition = 'none';
        clearInterval(autoScrollInterval);
    });

    carousel.addEventListener('touchend', () => {
        isDragging = false;
        carousel.style.transition = 'transform 0.5s ease-in-out';
        autoScrollInterval = setInterval(autoScroll, scrollInterval);
    });

    carousel.addEventListener('touchmove', (e) => {
        if (!isDragging) return;
        const x = e.touches[0].pageX - carousel.offsetLeft;
        const walk = (x - startX);
        currentPosition = scrollLeft - walk;

        const containerWidth = carousel.parentElement.offsetWidth;
        const trackWidth = carousel.scrollWidth;
        const maxPosition = trackWidth - containerWidth;

        if (currentPosition < 0) currentPosition = 0;
        if (currentPosition > maxPosition) currentPosition = maxPosition;

        carousel.style.transform = `translateX(-${currentPosition}px)`;
    });

    carousel.style.cursor = 'grab';
}

function updateOnlineUsersCount() {
    const onlineCountEl = document.getElementById('onlineUsersCount');
    if (!onlineCountEl) return;

    $.get('/api/user-sessions/online-count')
        .done(function(response) {
            if (response.success && response.onlineCount !== undefined) {
                onlineCountEl.textContent = response.onlineCount;
            }
        })
        .fail(function() {
            // Silently fail - keep showing last known count or 0
        });
}

function updateOnlineUsersDetails() {
    $.get('/api/user-sessions/online-users')
        .done(function(response) {
            if (response.success && response.onlineUsers) {
                // Update count badge
                const onlineCountEl = document.getElementById('onlineUsersCount');
                if (onlineCountEl) {
                    onlineCountEl.textContent = response.onlineCount;
                }
                // Update table
                displayOnlineUsers(response.onlineUsers);
            }
        })
        .fail(function() {
            // Silently fail
        });
}

function displayOnlineUsers(onlineUsers) {
    const onlineUsersList = document.getElementById('onlineUsersList');
    if (!onlineUsersList) return;

    if (onlineUsers.length === 0) {
        onlineUsersList.innerHTML = '<tr><td colspan="4" class="text-center">No users online</td></tr>';
        return;
    }

    let html = '';
    onlineUsers.forEach(function(user) {
        html += `
            <tr>
                <td>${user.username || 'N/A'}</td>
                <td>${user.officeName || 'N/A'}</td>
                <td>${user.areaName || 'N/A'}</td>
                <td>${user.loginTime || 'N/A'}</td>
            </tr>
        `;
    });

    onlineUsersList.innerHTML = html;
}

// ── Filter Panel Functionality ───────────────────────────────────────────
function initializeFilterPanel() {
    const toggleFilterBody = document.getElementById('toggleFilterBody');
    const filterBody = document.getElementById('filterBody');
    const filterChevron = document.getElementById('filterChevron');
    const applyFilters = document.getElementById('applyFilters');
    const resetFilters = document.getElementById('resetFilters');

    // Toggle filter body visibility
    if (toggleFilterBody && filterBody && filterChevron) {
        toggleFilterBody.addEventListener('click', function() {
            const isHidden = filterBody.style.display === 'none';
            filterBody.style.display = isHidden ? 'block' : 'none';
            filterChevron.className = isHidden ? 'fas fa-chevron-up' : 'fas fa-chevron-down';
        });
    }

    // Apply filters
    if (applyFilters) {
        applyFilters.addEventListener('click', function() {
            applyTableFilters();
        });
    }

    // Reset filters
    if (resetFilters) {
        resetFilters.addEventListener('click', function() {
            resetTableFilters();
        });
    }

    // Live filter search input as user types
    const searchInput = document.getElementById('tableSearchInput');
    if (searchInput) {
        searchInput.addEventListener('input', function() {
            applyTableFilters();
        });
        searchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                applyTableFilters();
            }
        });
    }

    // Live filter dropdowns on change + highlight styling
    const filterArea = document.getElementById('filterArea');
    if (filterArea) {
        filterArea.addEventListener('change', function() {
            this.classList.toggle('has-value', !!this.value);
            applyTableFilters();
        });
    }

    const filterConnectivity = document.getElementById('filterConnectivity');
    if (filterConnectivity) {
        filterConnectivity.addEventListener('change', function() {
            this.classList.toggle('has-value', !!this.value);
            applyTableFilters();
        });
    }

    const filterOfficeStatus = document.getElementById('filterOfficeStatus');
    if (filterOfficeStatus) {
        filterOfficeStatus.addEventListener('change', function() {
            this.classList.toggle('has-value', !!this.value);
            applyTableFilters();
        });
    }
}

function applyTableFilters() {
    const tableId = IS_ADMIN ? '#systemAdminTable' : '#officeTable';
    if (!$.fn.DataTable.isDataTable(tableId)) return;
    const table = $(tableId).DataTable();
    
    const searchValue = document.getElementById('tableSearchInput')?.value || '';
    const areaValue = document.getElementById('filterArea')?.value || '';
    const connectivityValue = document.getElementById('filterConnectivity')?.value || '';
    const officeStatusValue = document.getElementById('filterOfficeStatus')?.value || '';

    // Clear all existing custom search functions
    $.fn.dataTable.ext.search = [];

    // Apply global search
    table.search(searchValue);

    // Apply area filter for System Admin only
    if (IS_ADMIN && document.getElementById('filterArea')) {
        table.column(1).search(areaValue);
    }

    // Apply office status filter
    const officeStatusColumnIndex = IS_ADMIN ? 6 : (IS_AREA_ADMIN ? 6 : 5);
    table.column(officeStatusColumnIndex).search(officeStatusValue);

    // Apply connectivity filter with custom search for HTML badges
    if (connectivityValue) {
        $.fn.dataTable.ext.search.push(function(settings, data, dataIndex) {
            const tableIdFromSettings = settings.nTable.id;
            const isCorrectTable = (IS_ADMIN && tableIdFromSettings === 'systemAdminTable') || 
                                  (!IS_ADMIN && tableIdFromSettings === 'officeTable');
            
            if (!isCorrectTable) return true;
            
            // Get correct column index based on user role
            // System Admin: #(0) | Area(1) | Name(2) | Province(3) | City(4) | Conn(5) | Office(6) | Actions(7)
            //   → Area column IS in the DOM → Conn at DOM cell [5] ✓
            // Area Admin: logical #(0)|Name(1)|Area(2)|Province(3)|City(4)|Conn(5)|Office(6)
            //   → Area column hidden (visible:false = removed from DOM) → Conn shifts to DOM cell [4]
            // Regular User: #(0) | Name(1) | Province(2) | City(3) | Conn(4) | Office(5) | Actions(6)
            //   → No Area column → Conn at DOM cell [4]
            const connectivityColumnIndex = IS_ADMIN ? 5 : 4;
            const currentTable = IS_ADMIN ? 
                $('#systemAdminTable').DataTable() : 
                $('#officeTable').DataTable();
            
            const connectivityCell = currentTable.row(dataIndex).node().cells[connectivityColumnIndex];
            const badgeElement = connectivityCell.querySelector('.badge');
            
            if (!badgeElement) return true;
            
            const badgeText = badgeElement.textContent || badgeElement.innerText || '';
            // Remove icons and extra whitespace, then normalize
            const cleanText = badgeText
                .replace(/[\u25CF\u25CB●●]/g, '') // Remove bullet characters
                .replace(/\s+/g, ' ')           // Normalize whitespace
                .trim()
                .toLowerCase();
            
            console.log('Connectivity Badge Text:', badgeText, 'Clean Text:', cleanText, 'Filter Value:', connectivityValue, 'Role:', IS_ADMIN ? 'Admin' : (IS_AREA_ADMIN ? 'Area Admin' : 'User'));
            
            // Map filter values to badge text (use exact match — 'inactive'.includes('active') is true!)
            if (connectivityValue === 'Active') {
                return cleanText === 'active';
            } else if (connectivityValue === 'Inactive') {
                return cleanText === 'inactive';
            }
            
            return true;
        });
    }

    table.draw();
    updateActiveFilterCount();
}

function resetTableFilters() {
    const tableId = IS_ADMIN ? '#systemAdminTable' : '#officeTable';
    if (!$.fn.DataTable.isDataTable(tableId)) return;
    const table = $(tableId).DataTable();

    $.fn.dataTable.ext.search = [];
    table.search('').columns().search('').draw();

    const searchInput = document.getElementById('tableSearchInput');
    if (searchInput) {
        searchInput.value = '';
        searchInput.classList.remove('has-value');
    }
    const filterArea = document.getElementById('filterArea');
    if (filterArea) {
        filterArea.value = '';
        filterArea.classList.remove('has-value');
    }
    const filterConnectivity = document.getElementById('filterConnectivity');
    if (filterConnectivity) {
        filterConnectivity.value = '';
        filterConnectivity.classList.remove('has-value');
    }
    const filterOfficeStatus = document.getElementById('filterOfficeStatus');
    if (filterOfficeStatus) {
        filterOfficeStatus.value = '';
        filterOfficeStatus.classList.remove('has-value');
    }

    updateActiveFilterCount();
}

function updateActiveFilterCount() {
    const searchInput = document.getElementById('tableSearchInput')?.value || '';
    const areaValue = document.getElementById('filterArea')?.value || '';
    const connectivityValue = document.getElementById('filterConnectivity')?.value || '';
    const officeStatusValue = document.getElementById('filterOfficeStatus')?.value || '';

    const activeFilters = [searchInput, areaValue, connectivityValue, officeStatusValue].filter(v => v !== '').length;
    const badge = document.getElementById('activeFilterCount');
    
    if (badge) {
        if (activeFilters > 0) {
            badge.textContent = activeFilters;
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    }

    // Update summary text
    const tableId = IS_ADMIN ? '#systemAdminTable' : '#officeTable';
    if ($.fn.DataTable.isDataTable(tableId)) {
        const table = $(tableId).DataTable();
        const summaryText = document.getElementById('tableSummaryText');
        if (summaryText) {
            const total = table.data().length;
            const filtered = table.rows({search: 'applied'}).count();
            summaryText.textContent = filtered === total ? `${total} offices` : `${filtered} of ${total} offices`;
        }
    }
}

// ── System Admin Table Functionality ───────────────────────────────────────
function initializeSystemAdminTable() {
    if ($.fn.DataTable.isDataTable('#systemAdminTable')) {
        $('#systemAdminTable').DataTable().destroy();
    }

    // Check if Actions column exists in the HTML for SRD Operation users
    const hasActionsColumn = $('#systemAdminTable thead th').length > 7;
    const isSrdOperation = dashboardRoleAttr('is-srd-operation');
    
    const adminColumnDefs = [
        { targets: 0, width: '45px', orderable: false, className: 'dt-center', render: function(data, type, row, meta) {
            return meta.row + meta.settings._iDisplayStart + 1;
        }},
        { targets: 1, orderable: true },   // Area
        { targets: 2, orderable: true, render: function(data, type, row, meta) {
            if (!data) return 'N/A';
            var trNode = meta.settings.aoData[meta.row].nTr;
            var officeId = trNode ? trNode.getAttribute('data-office-id') : '';
            return '<a href="#" class="office-name-link" data-office-id="' + officeId + '" data-office-name="' + data.replace(/"/g, '&quot;') + '" onclick="openOfficeProfilePopup(this.dataset.officeId, this.dataset.officeName); return false;">' + data + '</a>';
        }}, // Name
        { targets: 3, orderable: true },   // Province
        { targets: 4, orderable: true },   // City/Municipality
        { targets: 5, width: '120px', orderable: true, className: 'dt-center' },  // Connection
        { targets: 6, width: '105px', orderable: true, className: 'dt-center' }   // Office Status
    ];
    
    // Only add Actions column definition if it exists in HTML
    if (hasActionsColumn && !isSrdOperation) {
        adminColumnDefs.push({ 
            targets: 7, 
            width: '120px', 
            orderable: false, 
            className: 'dt-center',
            searchable: false 
        }); // Actions
    }

    dashboardTable = $('#systemAdminTable').DataTable({
        pageLength: 10,
        lengthMenu: [10, 25, 50, 100],
        paging: true,
        ordering: true,
        info: true,
        searching: true,
        serverSide: false,

        columnDefs: adminColumnDefs,
        order: [[1, ''], [2, '']], // Sort by Area then Name

        language: {
            search: '',
            searchPlaceholder: 'Quick search...',
            lengthMenu: 'Show _MENU_ entries',
            info: 'Showing _START_–_END_ of _TOTAL_ offices',
            infoEmpty: 'No offices found',
            infoFiltered: '(filtered from _MAX_ total)',
            paginate: { first: '«', previous: '‹', next: '›', last: '»' },
            zeroRecords: 'No matching offices found'
        },

        dom: '<"dt-length-wrap"l>rt<"dt-footer d-flex align-items-center justify-content-between mt-3"ip>',
        responsive: true,
        initComplete: function() {
            updateActiveFilterCount();
        }
    });

    dashboardTable.on('draw', function() {
        updateActiveFilterCount();
    });
}

// ── Regular Office Table Functionality ───────────────────────────────────────
function initializeOfficeTable() {
    if ($.fn.DataTable.isDataTable('#officeTable')) {
        $('#officeTable').DataTable().destroy();
    }

    // Check if Actions column exists in the HTML for SRD Operation users
    const hasActionsColumnOffice = $('#officeTable thead th').length > 6;
    const isSrdOperation = dashboardRoleAttr('is-srd-operation');
    
    // Dynamic column definitions based on user role
    let userColumnDefs;
    
    if (IS_AREA_ADMIN) {
        // Area Admin: Base columns without Actions
        userColumnDefs = [
            { targets: 0, width: '60px', orderable: false, className: 'dt-center', render: function(data, type, row, meta) {
                return meta.row + meta.settings._iDisplayStart + 1;
            }}, // #
            { targets: 1, orderable: true, render: function(data, type, row, meta) {
                if (!data) return 'N/A';
                var trNode = meta.settings.aoData[meta.row].nTr;
                var officeId = trNode ? trNode.getAttribute('data-office-id') : '';
                return '<a href="#" class="office-name-link" data-office-id="' + officeId + '" data-office-name="' + data.replace(/"/g, '&quot;') + '" onclick="openOfficeProfilePopup(this.dataset.officeId, this.dataset.officeName); return false;">' + data + '</a>';
            }},   // Name
            // Hide Area column for Area Admin view (header + cells)
            { targets: 2, orderable: true, visible: false },   // Area
            { targets: 3, orderable: true },   // Province
            { targets: 4, orderable: true },   // City/Municipality
            { targets: 5, orderable: true, className: 'dt-center' }, // Connectivity
            { targets: 6, orderable: true, className: 'dt-center' }  // Office Status
        ];
        
        // Only add Actions column definition if it exists in HTML
        if (hasActionsColumnOffice && !isSrdOperation) {
            userColumnDefs.push({ 
                targets: 7, 
                width: '120px', 
                orderable: false, 
                className: 'dt-center',
                searchable: false 
            }); // Actions
        }
    } else {
        // Regular User: Base columns without Actions
        userColumnDefs = [
            { targets: 0, width: '60px', orderable: false, className: 'dt-center', render: function(data, type, row, meta) {
                return meta.row + meta.settings._iDisplayStart + 1;
            }}, // #
            { targets: 1, orderable: true, render: function(data, type, row, meta) {
                if (!data) return 'N/A';
                var trNode = meta.settings.aoData[meta.row].nTr;
                var officeId = trNode ? trNode.getAttribute('data-office-id') : '';
                return '<a href="#" class="office-name-link" data-office-id="' + officeId + '" data-office-name="' + data.replace(/"/g, '&quot;') + '" onclick="openOfficeProfilePopup(this.dataset.officeId, this.dataset.officeName); return false;">' + data + '</a>';
            }},   // Name
            { targets: 2, orderable: true },   // Province
            { targets: 3, orderable: true },   // City/Municipality
            { targets: 4, orderable: true, className: 'dt-center' }, // Connectivity
            { targets: 5, orderable: true, className: 'dt-center' }   // Office Status
        ];
        
        // Only add Actions column definition if it exists in HTML
        if (hasActionsColumnOffice && !isSrdOperation) {
            userColumnDefs.push({ 
                targets: 6, 
                width: '120px', 
                orderable: false, 
                className: 'dt-center',
                searchable: false 
            }); // Actions
        }
    }

    dashboardTable = $('#officeTable').DataTable({
        pageLength: 25,
        lengthMenu: [10, 25, 50, 100],
        paging: true,
        ordering: true,
        info: true,
        searching: true,
        serverSide: false,

        columnDefs: userColumnDefs,
        order: [[1, '']], // Sort by Name (Area hidden for Area Admin)

        language: {
            search: '',
            searchPlaceholder: 'Quick search...',
            lengthMenu: 'Show _MENU_ entries',
            info: 'Showing _START_–_END_ of _TOTAL_ offices',
            infoEmpty: 'No offices found',
            infoFiltered: '(filtered from _MAX_ total)',
            paginate: { first: '«', previous: '‹', next: '›', last: '»' },
            zeroRecords: 'No matching offices found'
        },

        dom: '<"dt-length-wrap"l>rt<"dt-footer d-flex align-items-center justify-content-between mt-3"ip>',
        responsive: true,
        initComplete: function() {
            updateActiveFilterCount();
        }
    });

    dashboardTable.on('draw', function() {
        updateActiveFilterCount();
    });
}

// ── Map Functionality ─────────────────────────────────────────────────────
function initializeMap() {
    // Initialize map if map element exists
    if (typeof L !== 'undefined' && document.getElementById('map')) {
        map = L.map('map').setView([14.5995, 120.9842], 6); // Philippines center

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors'
        }).addTo(map);

        // Add markers for offices
        addOfficeMarkers();
    }
}

function addOfficeMarkers() {
    // This would typically fetch office coordinates and add markers
    // Implementation depends on your data structure
}

// ── Export Functionality ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function() {
    const exportBtn = document.getElementById('exportBtn');
    if (exportBtn) {
        exportBtn.addEventListener('click', function() {
            exportTableData();
        });
    }

    const refreshBtn = document.getElementById('refreshBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', function() {
            location.reload();
        });
    }
});

// ── Edit Button Handler for Dashboard ─────────────────────────────────────
// Fetch office data via API using the office ID stored on the button
$(document).on('click', '.btn-edit', function () {
    var id = $(this).data('office-id');
    if (!id) return;

    $.getJSON('/api/postal-office/' + id)
        .done(function (d) {
            if (typeof window.openModal === 'function') {
                window.openModal(d);
            } else {
                Swal.fire('Error', 'Edit modal not properly loaded', 'error');
            }
        })
        .fail(function (xhr) {
            Swal.fire('Error', (xhr.responseJSON || {}).message || 'Failed to load office data.', 'error');
        });
});

function exportTableData() {
    const tableId = IS_ADMIN ? '#systemAdminTable' : '#officeTable';
    const table = $(tableId).DataTable();
    
    // Export to CSV
    const csvData = table.data().toArray().map(function(row) {
        return row.join(',');
    }).join('\n');
    
    const blob = new Blob([csvData], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'postal-offices-' + new Date().toISOString().split('T')[0] + '.csv';
    a.click();
    window.URL.revokeObjectURL(url);
}

// ── Cleanup ───────────────────────────────────────────────────────────────
window.addEventListener('beforeunload', function () {
    const tableId = IS_ADMIN ? '#systemAdminTable' : '#officeTable';
    if (dashboardTable && $.fn.DataTable.isDataTable(tableId)) {
        dashboardTable.destroy();
    }
    document.getElementById('editOfficeModal')?.remove();
});