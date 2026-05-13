// Inventory Page Specific JavaScript

// Set today's date
document.addEventListener('DOMContentLoaded', function() {
    const todayDateElement = document.getElementById('todayDate');
    if (todayDateElement) {
        todayDateElement.textContent = new Date().toLocaleDateString('en-PH', {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    }
});

// Modal functions
function openModal() {
    console.log('openModal() called');
    const modalTitle = document.getElementById('modalTitle');
    const modalSub = document.getElementById('modalSub');
    const itemForm = document.getElementById('itemForm');
    const fId = document.getElementById('f-id');
    const modalOverlay = document.getElementById('modalOverlay');

    if (modalTitle) modalTitle.textContent = 'Add New Item';
    if (modalSub) modalSub.textContent = 'Fill in the asset details below';
    if (itemForm) itemForm.reset();
    if (fId) fId.value = '';
    
    // Reinitialize office data for cascading dropdown
    initializeOfficeData();
    
    if (modalOverlay) {
        modalOverlay.classList.add('open');
        console.log('Modal opened');
    } else {
        console.error('Modal overlay not found');
    }
}

function closeModal() {
    const modalOverlay = document.getElementById('modalOverlay');
    if (modalOverlay) {
        modalOverlay.classList.remove('open');
    }
}

function handleOverlayClick(event) {
    if (event.target === event.currentTarget) {
        closeModal();
    }
}

function openEdit(id, name, description, category, isServiceable, employeeId, officeName, area, dateAcquired, amount, createdBy) {
    const modalTitle = document.getElementById('modalTitle');
    const modalSub = document.getElementById('modalSub');
    const fId = document.getElementById('f-id');
    const fName = document.getElementById('f-name');
    const fDesc = document.getElementById('f-desc');
    const fCat = document.getElementById('f-cat');
    const fSvc = document.getElementById('f-svc');
    const fEmp = document.getElementById('f-emp');
    const fOfficeName = document.getElementById('f-officeName');
    const fArea = document.getElementById('f-area');
    const fDate = document.getElementById('f-date');
    const fAmount = document.getElementById('f-amount');
    const fCreatedBy = document.getElementById('f-createdBy');
    const modalOverlay = document.getElementById('modalOverlay');

    if (modalTitle) modalTitle.textContent = 'Edit Item';
    if (modalSub) modalSub.textContent = 'Update the asset details below';
    if (fId) fId.value = id;
    if (fName) fName.value = name;
    if (fDesc) fDesc.value = description;
    if (fCat) fCat.value = category;
    if (fSvc) fSvc.value = isServiceable;
    if (fEmp) fEmp.value = employeeId;
    if (fOfficeName) fOfficeName.value = officeName;
    if (fArea) fArea.value = area;
    if (fDate) fDate.value = dateAcquired;
    if (fAmount) fAmount.value = amount;
    if (fCreatedBy) fCreatedBy.value = createdBy;
    if (modalOverlay) modalOverlay.classList.add('open');
}

// Initialize event listeners
document.addEventListener('DOMContentLoaded', function() {
    console.log('Inventory page JavaScript loaded');
    
    // Initialize office data for cascading dropdown
    try {
        initializeOfficeData();
        console.log('Office data initialized successfully');
    } catch (error) {
        console.error('Error initializing office data:', error);
    }
    
    // Handle modal overlay clicks
    const modalOverlay = document.getElementById('modalOverlay');
    if (modalOverlay) {
        modalOverlay.addEventListener('click', handleOverlayClick);
        console.log('Modal overlay event listener attached');
    } else {
        console.error('Modal overlay not found');
    }

    // Handle close button clicks
    const modalCloseButtons = document.querySelectorAll('.modal-x');
    modalCloseButtons.forEach(button => {
        button.addEventListener('click', closeModal);
    });

    // Handle cancel button clicks
    const cancelButton = document.querySelector('button.btn-outline');
    if (cancelButton) {
        cancelButton.addEventListener('click', closeModal);
    }

    // Handle Edit button clicks
    const editButtons = document.querySelectorAll('.act-btn.edit');
    console.log('Found edit buttons:', editButtons.length);
    editButtons.forEach(btn => {
        btn.addEventListener('click', function() {
            console.log('Edit button clicked for item:', this.getAttribute('data-id'));
            try {
                openEdit(
                    this.getAttribute('data-id'),
                    this.getAttribute('data-name'),
                    this.getAttribute('data-desc'),
                    this.getAttribute('data-cat'),
                    this.getAttribute('data-svc'),
                    this.getAttribute('data-emp'),
                    this.getAttribute('data-office'),
                    this.getAttribute('data-area'),
                    this.getAttribute('data-date'),
                    this.getAttribute('data-amt'),
                    this.getAttribute('data-by')
                );
            } catch (error) {
                console.error('Error opening edit modal:', error);
            }
        });
    });

    // Handle Print Tag button clicks
    document.querySelectorAll('.act-btn.print').forEach(btn => {
        btn.addEventListener('click', function() {
            console.log('Print tag button clicked for item:', this.getAttribute('data-id'));
            openPrintTagModal(this.getAttribute('data-id'));
        });
    });

    // Handle Add Item button click
    const addItemBtn = document.getElementById('add-item-btn');
    if (addItemBtn) {
        addItemBtn.addEventListener('click', function() {
            console.log('Add Item button clicked');
            openModal();
        });
    }

    // Handle area dropdown change for cascading office filtering
    const areaSelect = document.getElementById('f-area');
    if (areaSelect) {
        areaSelect.addEventListener('change', filterOfficesByArea);
    }
});

// Store office data globally for cascading dropdown
if (typeof officeData === 'undefined') {
    window.officeData = [];
}

// Initialize office data when page loads
function initializeOfficeData() {
    const officeSelect = document.getElementById('f-officeName');
    if (officeSelect) {
        const options = officeSelect.querySelectorAll('option[data-area]');
        // Clear existing data without redeclaring
        window.officeData.length = 0;
        options.forEach(option => {
            const area = option.getAttribute('data-area');
            if (area) { // Only include options that have area data
                window.officeData.push({
                    value: option.value,
                    text: option.textContent,
                    area: area
                });
            }
        });
        console.log('Office data loaded:', window.officeData.length, 'offices');
    }
}

// Cascading dropdown functionality for area and office selection
function filterOfficesByArea() {
    const areaSelect = document.getElementById('f-area');
    const officeSelect = document.getElementById('f-officeName');
    
    if (!areaSelect || !officeSelect) {
        console.error('Area or office select not found');
        return;
    }
    
    const selectedArea = areaSelect.value;
    console.log('Filtering offices by area:', selectedArea);
    
    // Store current office selection
    const currentOffice = officeSelect.value;
    
    // Clear office dropdown
    officeSelect.innerHTML = '<option value="">Select office…</option>';
    
    // If no area selected, show all offices from original HTML
    if (selectedArea === '') {
        // Reload original options from the page
        const originalSelect = document.querySelector('#f-officeName option[data-area]');
        if (originalSelect) {
            const container = originalSelect.parentElement;
            const originalOptions = Array.from(container.querySelectorAll('option[data-area]'));
            originalOptions.forEach(option => {
                officeSelect.appendChild(option.cloneNode(true));
            });
        }
    } else {
        // Filter offices based on selected area
        window.officeData.forEach(office => {
            if (office.area === selectedArea) {
                const option = document.createElement('option');
                option.value = office.value;
                option.textContent = office.text;
                option.setAttribute('data-area', office.area);
                officeSelect.appendChild(option);
            }
        });
    }
    
    // Try to restore previous office selection if it's still available
    if (currentOffice) {
        officeSelect.value = currentOffice;
    }
    
    console.log('Office filtering completed');
}

// Re-attach event listeners to action buttons after AJAX update
function reattachActionListeners() {
    console.log('Re-attaching action listeners...');
    
    // Handle Edit button clicks
    const editButtons = document.querySelectorAll('.act-btn.edit');
    console.log('Found edit buttons:', editButtons.length);
    editButtons.forEach(btn => {
        btn.addEventListener('click', function() {
            console.log('Edit button clicked for item:', this.getAttribute('data-id'));
            try {
                openEdit(
                    this.getAttribute('data-id'),
                    this.getAttribute('data-name'),
                    this.getAttribute('data-desc'),
                    this.getAttribute('data-cat'),
                    this.getAttribute('data-svc'),
                    this.getAttribute('data-emp'),
                    this.getAttribute('data-office'),
                    this.getAttribute('data-area'),
                    this.getAttribute('data-date'),
                    this.getAttribute('data-amt'),
                    this.getAttribute('data-by')
                );
            } catch (error) {
                console.error('Error opening edit modal:', error);
            }
        });
    });

    // Handle Print Tag button clicks
    const printButtons = document.querySelectorAll('.act-btn.print');
    console.log('Found print buttons:', printButtons.length);
    printButtons.forEach(btn => {
        btn.addEventListener('click', function() {
            console.log('Print tag button clicked for item:', this.getAttribute('data-id'));
            openPrintTagModal(this.getAttribute('data-id'));
        });
    });
    
    console.log('Action listeners re-attached successfully');
}

// Debounce function for search
function debounceSubmit() {
    console.log('Debounce submit called');
    clearTimeout(window._debounceTimer);
    window._debounceTimer = setTimeout(() => {
        const form = document.getElementById('filterForm');
        if (!form) {
            console.error('Filter form not found');
            return;
        }
        
        const formData = new FormData(form);
        const params = new URLSearchParams(formData);
        console.log('Search params:', params.toString());
        
        fetch(`${form.action}?${params.toString()}`)
            .then(response => {
                console.log('Search response status:', response.status);
                return response.text();
            })
            .then(html => {
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');
                const newTableBody = doc.querySelector('tbody');
                const currentTableBody = document.querySelector('tbody');
                
                if (newTableBody && currentTableBody) {
                    currentTableBody.innerHTML = newTableBody.innerHTML;
                    console.log('Table updated successfully');
                    // Re-attach event listeners to new action buttons
                    reattachActionListeners();
                } else {
                    console.error('Table bodies not found');
                }
                
                // Update URL without page reload
                history.pushState(null, '', `${form.action}?${params.toString()}`);
            })
            .catch(error => {
                console.error('Search error:', error);
            });
    }, 500);
}

// Export functions for global access
window.inventoryPage = {
    openModal,
    closeModal,
    handleOverlayClick,
    openEdit,
    debounceSubmit
};

// Also make functions globally accessible for sidebar buttons
window.openModal = openModal;
window.closeModal = closeModal;
window.handleOverlayClick = handleOverlayClick;
window.openEdit = openEdit;
window.debounceSubmit = debounceSubmit;
