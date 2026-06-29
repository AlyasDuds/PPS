/**
 * report.js — Connectivity Report Page
 * Filter panel, print, and Excel export functionality
 */

$(document).ready(function () {
    initializeFilterPanel();
    initializePrint();
    initializeExportExcel();
    initializeReportTable();
});

/* ── Toggle expandable office list in QB cards ── */
function toggleQbList(btn) {
    var list = btn.nextElementSibling;
    if (!list || !list.classList.contains('qb-list')) return;
    var isOpen = list.classList.toggle('open');
    btn.classList.toggle('open', isOpen);
}

/* ── Toggle names in table cells ── */
function toggleNames(btn) {
    var list = btn.parentElement.querySelector('.qb-names-list');
    if (!list) return;
    var isOpen = list.classList.toggle('open');
    btn.innerHTML = isOpen
        ? '<i class="fas fa-chevron-up mr-1"></i>hide offices'
        : '<i class="fas fa-chevron-down mr-1"></i>show offices';
}

/**
 * Navigate to postal office profile page.
 * data-office-id is set by Thymeleaf from the "ID::" prefix in the entry string.
 * source=report tells the Back button on the profile page to return here.
 */
function goToProfile(el) {
    var id = el.getAttribute('data-office-id');
    if (!id || id.trim() === '') return;
    // Save current URL (with filters) so the Back button on profile works
    sessionStorage.setItem('reportReturnUrl', window.location.href);
    window.location.href = '/profile/' + id.trim() + '?source=report';
}

/* =====================================================
   FILTER PANEL
===================================================== */
function initializeFilterPanel() {
    // Cache these — they're queried repeatedly
    const $year = $('#yearSelector');
    const $quarter = $('#quarterFilter');
    const $area = $('#areaFilter');
    const $status = $('#statusFilter');
    const $filters = $year.add($quarter).add($area).add($status);

    highlightActiveSelects();
    renderFilterTags();

    $('#toggleFiltersBtn').on('click', function () {
        $('#filterBody').toggleClass('collapsed');
        $('#filterChevron').toggleClass('fa-chevron-up fa-chevron-down');
    });

    $('#applyFiltersBtn').on('click', applyFilters);
    $('#clearFiltersBtn').on('click', clearFilters);

    $filters.on('change', function () {
        highlightActiveSelects();
        renderFilterTags();
    }).on('keypress', function (e) {
        if (e.key === 'Enter') applyFilters();
    });
}

function applyFilters() {
    var year = $('#yearSelector').val();
    var quarter = $('#quarterFilter').val();
    var area = $('#areaFilter').val();
    var status = $('#statusFilter').val();

    var params = [];
    if (year) params.push('year=' + encodeURIComponent(year));
    if (quarter) params.push('quarterFilter=' + encodeURIComponent(quarter));
    if (area) params.push('areaFilter=' + encodeURIComponent(area));
    if (status) params.push('statusFilter=' + encodeURIComponent(status));

    // Show loading state immediately for perceived performance
    $('#applyFiltersBtn')
        .prop('disabled', true)
        .html('<i class="fas fa-spinner fa-spin mr-1"></i> Applying...');

    // Use replace instead of href to skip browser history entry (slightly faster)
    window.location.replace('/report' + (params.length ? '?' + params.join('&') : ''));
}

function renderFilterTags() {
    var container = $('#activeFilterTags');
    container.empty();

    var year = $('#yearSelector').val();
    var quarter = $('#quarterFilter').val();
    var areaVal = $('#areaFilter').val();
    var areaText = $('#areaFilter option:selected').text().trim();
    var status = $('#statusFilter').val();

    var qLabels = { Q1: 'Q1 (Jan–Mar)', Q2: 'Q2 (Apr–Jun)', Q3: 'Q3 (Jul–Sep)', Q4: 'Q4 (Oct–Dec)' };
    var sLabels = {
        active: 'Active',
        inactive: 'Inactive',
        newly_connected: 'Newly Connected',
        newly_disconnected: 'Newly Disconnected'
    };

    if (year) container.append(buildTag('tag-year', 'fas fa-calendar', 'Year: ' + year, 'yearSelector'));
    if (quarter) container.append(buildTag('tag-quarter', 'fas fa-layer-group', qLabels[quarter] || quarter, 'quarterFilter'));
    if (areaVal) container.append(buildTag('tag-area', 'fas fa-map-marker-alt', 'Area: ' + areaText, 'areaFilter'));
    if (status) container.append(buildTag('tag-status-' + status.replace('_', '-'), 'fas fa-wifi', sLabels[status] || status, 'statusFilter'));
}

function buildTag(extraClass, iconClass, text, selectId) {
    return $('<span class="filter-tag ' + extraClass + '">' +
        '<i class="' + iconClass + ' mr-1"></i>' + text +
        '<button class="remove-tag" data-target="' + selectId + '" title="Remove">' +
        '<i class="fas fa-times"></i></button></span>')
        .on('click', '.remove-tag', function () {
            $('#' + $(this).data('target')).val('');
            highlightActiveSelects();
            renderFilterTags();
        });
}

/* =====================================================
   PRINT
===================================================== */
function initializePrint() {
    $('#printReportBtn').on('click', function () {
        var $btn = $(this);
        $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin mr-1"></i> Preparing...');

        var year = $('#yearSelector option:selected').text().trim() || 'All Years';
        var quarter = $('#quarterFilter option:selected').text().trim() || 'All Quarters';
        var area = $('#areaFilter option:selected').text().trim() || 'All Areas';
        var status = $('#statusFilter option:selected').text().trim() || 'All Status';

        var connected = $('.border-bottom-success h2').first().text().trim();
        var disconnected = $('.border-bottom-danger h2').first().text().trim();
        var total = $('.border-bottom-primary h2').first().text().trim();

        // Build table rows
        // Column order: Year(0) | Quarter(1) | Connected(2) | Newly Connected(3) | Disconnected(4) | Newly Disconnected(5) | Total(6) | Status(7)
        var tableRows = '';
        $('#quarterlyBreakdownTable tbody tr').each(function () {
            var cells = $(this).find('td');
            if (cells.length < 7) return;
            var isCurrent = $(this).hasClass('table-info') || $(this).hasClass('font-weight-bold');
            var rowStyle = isCurrent ? 'background:#eef2ff;font-weight:bold;' : '';

            var year = cells.eq(0).text().trim();
            var quarter = cells.eq(1).text().trim();
            var conn = cells.eq(2).find('.font-weight-bold').text().trim() || cells.eq(2).text().trim() || '—';
            var newlyConn = cells.eq(3).find('.badge-success').text().trim() || cells.eq(3).find('.text-muted').text().trim() || cells.eq(3).text().trim() || '—';
            var disconn = cells.eq(4).find('.font-weight-bold').text().trim() || cells.eq(4).text().trim() || '—';
            var newlyDisc = cells.eq(5).find('.badge-danger').text().trim() || cells.eq(5).find('.text-muted').text().trim() || cells.eq(5).text().trim() || '—';
            var total = cells.eq(6).text().trim() || '—';
            var status = cells.eq(7).text().trim() || '—';

            tableRows += '<tr style="' + rowStyle + '">';
            tableRows += '<td>' + year + '</td>';
            tableRows += '<td>' + quarter + '</td>';
            tableRows += '<td style="text-align:center;color:#1a9e72;font-weight:600;">' + conn + '</td>';
            tableRows += '<td style="text-align:center;color:#28a745;font-weight:600;">' + newlyConn + '</td>';
            tableRows += '<td style="text-align:center;color:#c0392b;font-weight:600;">' + disconn + '</td>';
            tableRows += '<td style="text-align:center;color:#dc3545;font-weight:600;">' + newlyDisc + '</td>';
            tableRows += '<td style="text-align:center;color:#2e59d9;font-weight:600;">' + total + '</td>';
            tableRows += '<td style="text-align:center;">' + status + '</td>';
            tableRows += '</tr>';
        });

        var printDate = new Date().toLocaleDateString('en-PH', {
            year: 'numeric', month: 'long', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });

        var html = '<!DOCTYPE html><html><head><meta charset="utf-8">' +
            '<title>PHLPost Connectivity Report</title>' +
            '<style>' +
            '* { box-sizing: border-box; margin: 0; padding: 0; }' +
            'body { font-family: Arial, sans-serif; background-color: #fff; color: #333; padding: 28px; }' +
            '* { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }' +
            
            /* Header styling */
            '.report-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; }' +
            '.logo-title-section { display: flex; align-items: center; gap: 15px; }' +
            '.report-logo { height: 50px; }' +
            '.logo-title-section h2 { color: #002e6e; font-size: 18px; margin: 0; }' +
            '.logo-title-section p { color: #666; font-size: 11px; margin: 0; }' +
            '.timestamp { font-size: 11px; color: #555; text-align: right; }' +
            '.header-line { border: none; border-top: 3px solid #002e6e; margin-bottom: 20px; }' +

            /* Flat Filter Box */
            '.filter-box-print { display: flex; justify-content: space-around; border: 1px solid #ccc; border-radius: 4px; padding: 10px; margin-bottom: 25px; background-color: #fff; }' +
            '.filter-item { font-size: 11pt; color: #002e6e; font-weight: bold; }' +

            /* KPI Cards */
            '.kpi-container { display: flex; justify-content: space-between; gap: 15px; margin-bottom: 25px; }' +
            '.kpi-card { flex: 1; text-align: center; padding: 15px; border-radius: 4px; background: #fff !important; }' +
            '.kpi-card small { font-size: 10px; text-transform: uppercase; letter-spacing: 0.6px; color: #888; display: block; margin-bottom: 8px; }' +
            '.kpi-card h1 { font-size: 26px; font-weight: 700; margin: 0; }' +
            '.kpi-card.active-card { border: 1px solid #28a745; color: #28a745; }' +
            '.kpi-card.inactive-card { border: 1px solid #dc3545; color: #dc3545; }' +
            '.kpi-card.total-card { border: 1px solid #0056b3; color: #0056b3; }' +

            /* Section Titles */
            '.section-title { color: #002e6e; font-size: 13px; font-weight: bold; margin-top: 20px; margin-bottom: 8px; border-left: 4px solid #002e6e; padding-left: 8px; }' +

            /* Table Design */
            '.report-table { width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 12px; }' +
            '.report-table th { background-color: #002e6e !important; color: #ffffff !important; text-align: left; padding: 8px; font-weight: bold; }' +
            '.report-table td { padding: 8px; border-bottom: 1px solid #e0e0e0; }' +
            'tbody tr:nth-child(even) { background: #f8f9fc; }' +

            /* Color adjustments */
            '.text-success { color: #28a745 !important; font-weight: bold; }' +
            '.text-danger { color: #dc3545 !important; font-weight: bold; }' +
            '.text-primary { color: #0056b3 !important; font-weight: bold; }' +

            /* Office Cards Grid */
            '.offices-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 20px; }' +
            '.office-card { border: 1px solid #e0e0e0; border-radius: 6px; padding: 12px; background: #fff; }' +
            '.office-card.disconnected-card { border-left: 4px solid #dc3545; }' +
            '.office-card:not(.disconnected-card) { border-left: 4px solid #28a745; }' +
            '.office-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }' +
            '.office-year-quarter { font-size: 10px; font-weight: bold; color: #666; text-transform: uppercase; }' +
            '.office-area { font-size: 10px; background: #e8ecf8; color: #002e6e; padding: 2px 8px; border-radius: 10px; font-weight: 600; }' +
            '.office-card-body { font-size: 12px; font-weight: 500; color: #333; }' +
            '.no-data { text-align: center; color: #999; font-style: italic; padding: 20px; }' +

            '.footer { margin-top: 22px; font-size: 10px; color: #aaa; text-align: center; border-top: 1px solid #eee; padding-top: 10px; }' +
            '@page { size: portrait; margin: 10mm; }' +
            '</style></head><body>' +

            '<div class="report-header">' +
            '  <div class="logo-title-section">' +
            '    <div>' +
            '      <h2>PHLPost — Connectivity Report</h2>' +
            '      <p>Philippine Postal Corporation • Profile System</p>' +
            '    </div>' +
            '  </div>' +
            '  <div class="timestamp">' +
            '    Generated:<br>' + printDate +
            '  </div>' +
            '</div>' +

            '<hr class="header-line">' +

            '<div class="filter-box-print">' +
            '  <span class="filter-item">&#128197; ' + year + '</span>' +
            '  <span class="filter-item">&#128200; ' + quarter + '</span>' +
            '  <span class="filter-item">&#128205; ' + area + '</span>' +
            '  <span class="filter-item">&#128246; ' + status + '</span>' +
            '</div>' +

            '<div class="kpi-container">' +
            '  <div class="kpi-card active-card"><small>ACTIVE (CONNECTED)</small><h1>' + connected + '</h1></div>' +
            '  <div class="kpi-card inactive-card"><small>INACTIVE (DISCONNECTED)</small><h1>' + disconnected + '</h1></div>' +
            '  <div class="kpi-card total-card"><small>TOTAL OFFICES</small><h1>' + total + '</h1></div>' +
            '</div>' +

            '<h3 class="section-title">Quarterly Breakdown</h3>' +
            '<table class="report-table">' +
            '<thead><tr><th>Year</th><th>Quarter</th><th>Connected</th><th>Newly Connected</th><th>Disconnected</th><th>Newly Disconnected</th><th>Total</th><th>Status</th></tr></thead>' +
            '<tbody>' + tableRows + '</tbody>' +
            '</table>' +

            // Newly Connected Offices Cards
            '<h3 class="section-title">Newly Connected Offices</h3>' +
            '<div class="offices-grid">' + buildNewlyConnectedCards() + '</div>' +

            // Newly Disconnected Offices Cards
            '<h3 class="section-title">Newly Disconnected Offices</h3>' +
            '<div class="offices-grid">' + buildNewlyDisconnectedCards() + '</div>' +

            '<div class="footer">PHLPost Profile System — Connectivity Report — Confidential — ' + printDate + '</div>' +
            '</body></html>';

        // Write to hidden iframe and print — avoids popup blockers & onload issues
        var iframe = document.getElementById('printFrame');
        if (!iframe) {
            iframe = document.createElement('iframe');
            iframe.id = 'printFrame';
            iframe.style.cssText = 'position:fixed;top:-9999px;left:-9999px;width:1px;height:1px;border:none;';
            document.body.appendChild(iframe);
        }

        var doc = iframe.contentWindow.document;
        doc.open();
        doc.write(html);
        doc.close();

        // Wait for iframe to fully render then print
        setTimeout(function () {
            iframe.contentWindow.focus();
            iframe.contentWindow.print();
            $btn.prop('disabled', false).html('<i class="fas fa-print mr-1"></i> Print Report');
        }, 600);
    });
}

/* Helper functions to build office detail tables */
function buildNewlyConnectedTable() {
    var rows = '';
    $('#quarterlyBreakdownTable tbody tr').each(function () {
        var cells = $(this).find('td');
        // Standard check: Ensure row has enough columns
        if (cells.length < 5) return;

        var year = cells.eq(0).text().trim();
        var quarter = cells.eq(1).text().trim();

        // In PO TEMPLATE structure, 'Newly Connected' is in column 3 (0-indexed)
        // This should contain the count and the office list
        var newlyConnectedCell = cells.eq(3);

        var officeList = newlyConnectedCell.find('.qb-names-list');
        if (officeList.length > 0) {
            officeList.find('.qb-names-item').each(function () {
                var areaTag = $(this).find('.qb-area-tag').text().trim();
                // Get office name from the span that comes after the area tag
                var officeName = $(this).find('span').not('.qb-area-tag').first().text().trim();

                if (officeName) {
                    rows += '<tr>';
                    rows += '<td>' + year + '</td>';
                    rows += '<td>' + quarter + '</td>';
                    rows += '<td>' + officeName + '</td>';
                    rows += '<td>' + areaTag + '</td>';
                    rows += '</tr>';
                }
            });
        }
    });

    if (!rows) {
        rows = '<tr><td colspan="4" class="text-center text-muted">No newly connected offices found</td></tr>';
    }

    return rows;
}

function buildNewlyDisconnectedTable() {
    var rows = '';
    $('#quarterlyBreakdownTable tbody tr').each(function () {
        var cells = $(this).find('td');
        // Standard check: Ensure row has enough columns
        if (cells.length < 6) return;

        var year = cells.eq(0).text().trim();
        var quarter = cells.eq(1).text().trim();

        // In PO TEMPLATE structure, 'Newly Disconnected' is in column 5 (0-indexed)
        // This should contain count and office list
        var newlyDisconnectedCell = cells.eq(5);

        var officeList = newlyDisconnectedCell.find('.qb-names-list');
        if (officeList.length > 0) {
            officeList.find('.qb-names-item').each(function () {
                var areaTag = $(this).find('.qb-area-tag').text().trim();
                // Get the office name from the span that comes after the area tag
                var officeName = $(this).find('span').not('.qb-area-tag').first().text().trim();

                if (officeName) {
                    rows += '<tr>';
                    rows += '<td>' + year + '</td>';
                    rows += '<td>' + quarter + '</td>';
                    rows += '<td>' + officeName + '</td>';
                    rows += '<td>' + areaTag + '</td>';
                    rows += '</tr>';
                }
            });
        }
    });

    if (!rows) {
        rows = '<tr><td colspan="4" class="text-center text-muted">No newly disconnected offices found</td></tr>';
    }

    return rows;
}

function buildNewlyConnectedCards() {
    var cards = '';
    var groupedData = {};

    $('#quarterlyBreakdownTable tbody tr').each(function () {
        var cells = $(this).find('td');
        if (cells.length < 5) return;

        var year = cells.eq(0).text().trim();
        var quarter = cells.eq(1).text().trim();
        var newlyConnectedCell = cells.eq(3);

        var key = year + ' - ' + quarter;
        if (!groupedData[key]) {
            groupedData[key] = [];
        }

        var officeList = newlyConnectedCell.find('.qb-names-list');
        if (officeList.length > 0) {
            officeList.find('.qb-names-item').each(function () {
                var areaTag = $(this).find('.qb-area-tag').text().trim();
                var officeName = $(this).find('span').not('.qb-area-tag').first().text().trim();

                if (officeName) {
                    groupedData[key].push({
                        officeName: officeName,
                        areaTag: areaTag
                    });
                }
            });
        }
    });

    for (var key in groupedData) {
        if (groupedData[key].length > 0) {
            cards += '<div class="office-card">';
            cards += '<div class="office-card-header">';
            cards += '<span class="office-year-quarter">' + key + '</span>';
            cards += '<span class="office-count">' + groupedData[key].length + ' offices</span>';
            cards += '</div>';
            cards += '<div class="office-card-body">';
            groupedData[key].forEach(function (office) {
                cards += '<div class="office-item">';
                cards += '<span class="office-area">' + office.areaTag + '</span>';
                cards += '<span class="office-name">' + office.officeName + '</span>';
                cards += '</div>';
            });
            cards += '</div>';
            cards += '</div>';
        }
    }

    if (!cards) {
        cards = '<div class="no-data">No newly connected offices found</div>';
    }

    return cards;
}

function buildNewlyDisconnectedCards() {
    var cards = '';
    var groupedData = {};

    $('#quarterlyBreakdownTable tbody tr').each(function () {
        var cells = $(this).find('td');
        if (cells.length < 6) return;

        var year = cells.eq(0).text().trim();
        var quarter = cells.eq(1).text().trim();
        var newlyDisconnectedCell = cells.eq(5);

        var key = year + ' - ' + quarter;
        if (!groupedData[key]) {
            groupedData[key] = [];
        }

        var officeList = newlyDisconnectedCell.find('.qb-names-list');
        if (officeList.length > 0) {
            officeList.find('.qb-names-item').each(function () {
                var areaTag = $(this).find('.qb-area-tag').text().trim();
                var officeName = $(this).find('span').not('.qb-area-tag').first().text().trim();

                if (officeName) {
                    groupedData[key].push({
                        officeName: officeName,
                        areaTag: areaTag
                    });
                }
            });
        } 
    });

    for (var key in groupedData) {
        if (groupedData[key].length > 0) {
            cards += '<div class="office-card disconnected-card">';
            cards += '<div class="office-card-header">';
            cards += '<span class="office-year-quarter">' + key + '</span>';
            cards += '<span class="office-count">' + groupedData[key].length + ' offices</span>';
            cards += '</div>';
            cards += '<div class="office-card-body">';
            groupedData[key].forEach(function (office) {
                cards += '<div class="office-item">';
                cards += '<span class="office-area">' + office.areaTag + '</span>';
                cards += '<span class="office-name">' + office.officeName + '</span>';
                cards += '</div>';
            });
            cards += '</div>';
            cards += '</div>';
        }
    }

    if (!cards) {
        cards = '<div class="no-data">No newly disconnected offices found</div>';
    }

    return cards;
}

/* =====================================================
   EXPORT EXCEL
===================================================== */
function initializeExportExcel() {
    $('#exportExcelBtn').on('click', function () {
        var $btn = $(this);

        if (typeof XLSX === 'undefined') {
            Swal.fire({
                icon: 'error',
                title: 'Export Error',
                text: 'Excel library not loaded. Please refresh the page and try again.',
                confirmButtonColor: '#002868'
            });
            return;
        }

        var data = [];
        // Header based on Quarterly Breakdown Table
        data.push(['Year', 'Quarter', 'Connected', 'Newly Connected', 'Disconnected', 'Newly Disconnected', 'Total', 'Status']);

        var hasData = false;

        // Extract data from quarterly breakdown table
        $('#quarterlyBreakdownTable tbody tr').each(function (index) {
            var cells = $(this).find('td');
            if (cells.length > 0) {
                hasData = true;
                // Helper function to truncate text to avoid Excel limit
                function truncateText(text, maxLength) {
                    if (!text) return 'N/A';
                    text = text.trim();
                    if (text.length > maxLength) {
                        return text.substring(0, maxLength);
                    }
                    return text;
                }

                data.push([
                    truncateText(cells.eq(0).text(), 100), // Year
                    truncateText(cells.eq(1).text(), 50),  // Quarter
                    truncateText(cells.eq(2).text(), 100), // Connected
                    truncateText(cells.eq(3).text(), 100), // Newly Connected
                    truncateText(cells.eq(4).text(), 100), // Disconnected
                    truncateText(cells.eq(5).text(), 100), // Newly Disconnected
                    truncateText(cells.eq(6).text(), 100), // Total
                    truncateText(cells.eq(7).text(), 50)   // Status
                ]);
            }
        });

        if (!hasData) {
            console.log('Export Debug: No data found. Checking table status...');

            // Additional debugging information
            let debugInfo = {
                tableExists: $('#quarterlyBreakdownTable').length > 0,
                currentFilters: {
                    year: $('#yearSelector').val(),
                    quarter: $('#quarterFilter').val(),
                    area: $('#areaFilter').val(),
                    status: $('#statusFilter').val()
                },
                tableRows: $('#quarterlyBreakdownTable tbody tr').length
            };

            console.log('Export Debug Info:', debugInfo);

            let message = 'There is no data to export for the current filters.';
            if (debugInfo.tableRows === 0) {
                message += ' The table appears to be empty. Try clearing filters or refreshing the page.';
            }

            Swal.fire({
                icon: 'warning',
                title: 'No Data',
                text: message,
                confirmButtonColor: '#002868'
            });
            return;
        }

        $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin mr-1"></i> Exporting...');

        var year = $('#yearSelector').val() || 'All';
        var quarter = $('#quarterFilter').val() || 'All';
        var areaText = $('#areaFilter option:selected').text().trim() || 'All Areas';
        var statText = $('#statusFilter option:selected').text().trim() || 'All Status';

        var connected = $('.border-bottom-success h2').first().text().trim();
        var disconnected = $('.border-bottom-danger h2').first().text().trim();
        var total = $('.border-bottom-primary h2').first().text().trim();
        var printDate = new Date().toLocaleDateString('en-PH', { year: 'numeric', month: 'long', day: 'numeric' });

        var wb = XLSX.utils.book_new();

        // Sheet 1 — Connectivity Data (based on PO Template)
        var ws = XLSX.utils.aoa_to_sheet(data);
        ws['!cols'] = [
            { wch: 12 }, { wch: 25 }, { wch: 15 }, { wch: 20 },
            { wch: 18 }, { wch: 20 }, { wch: 18 }, { wch: 12 }, { wch: 20 }
        ];
        XLSX.utils.book_append_sheet(wb, ws, 'Connectivity Data');

        // Sheet 2 — Summary
        var summaryData = [
            ['PHLPost — Connectivity Report'],
            ['Generated:', printDate],
            [''],
            ['FILTERS APPLIED', ''],
            ['Year', year],
            ['Quarter', quarter],
            ['Area', areaText],
            ['Status', statText],
            [''],
            ['OVERALL STATISTICS', ''],
            ['Active (Connected)', Number(connected) || connected],
            ['Inactive (Disconnected)', Number(disconnected) || disconnected],
            ['Total Offices', Number(total) || total]
        ];
        var wsSummary = XLSX.utils.aoa_to_sheet(summaryData);
        wsSummary['!cols'] = [{ wch: 28 }, { wch: 24 }];
        XLSX.utils.book_append_sheet(wb, wsSummary, 'Summary');

        var filename = 'PHLPost_Connectivity_' + new Date().toISOString().slice(0, 10) + '.xlsx';
        XLSX.writeFile(wb, filename);

        setTimeout(function () {
            $btn.prop('disabled', false).html('<i class="fas fa-file-excel mr-1"></i> Export Excel');
        }, 800);
    });
}

/* =====================================================
   REPORT TABLE WITH ARCHIVE FUNCTIONALITY
===================================================== */
function initializeReportTable() {
    if (!$('#postOfficeTable').length) return;

    if ($.fn.DataTable.isDataTable('#postOfficeTable')) {
        $('#postOfficeTable').DataTable().destroy();
        $('#postOfficeTable').empty();
    }

    // Read filter values
    const yearFilter = $('#yearSelector').val() || '';
    const quarterFilter = $('#quarterFilter').val() || '';
    const areaFilter = $('#areaFilter').val() || '';
    const statusFilter = $('#statusFilter').val() || '';

    // Build AJAX URL with filter parameters
    let ajaxUrl = '/api/quarters/post-offices';
    const params = [];

    if (yearFilter) params.push('year=' + encodeURIComponent(yearFilter));
    if (quarterFilter) params.push('quarter=' + encodeURIComponent(quarterFilter));
    if (areaFilter) params.push('area=' + encodeURIComponent(areaFilter));
    if (statusFilter) params.push('status=' + encodeURIComponent(statusFilter));

    if (params.length > 0) {
        ajaxUrl += '?' + params.join('&');
    }

    const table = $('#postOfficeTable').DataTable({
        processing: true,
        serverSide: false,
        ajax: {
            url: ajaxUrl,
            dataSrc: '',
            error: function (xhr, error, code) {
                console.error('DataTable AJAX Error:', {
                    status: xhr.status,
                    statusText: xhr.statusText,
                    response: xhr.responseText,
                    error: error,
                    code: code
                });

                let errorMessage = 'Failed to load post office data.';
                if (xhr.responseJSON && xhr.responseJSON.error) {
                    errorMessage = 'Server error: ' + xhr.responseJSON.message;
                } else if (xhr.status === 0) {
                    errorMessage = 'Network error. Please check your connection.';
                } else if (xhr.status === 500) {
                    errorMessage = 'Server error. Please try again later.';
                }

                if (typeof Swal !== 'undefined') {
                    Swal.fire({
                        icon: 'error',
                        title: 'Data Loading Error',
                        text: errorMessage,
                        confirmButtonText: 'OK'
                    });
                } else {
                    alert(errorMessage);
                }
            }
        },
        columns: [
            { data: null, render: (data, type, row, meta) => meta.row + 1 },
            { data: 'area', render: d => d || 'N/A' }, // From Column A: AREA
            { data: 'name', defaultContent: 'N/A' },    // From Column B: POSTAL OFFICE NAME
            { data: 'province', defaultContent: 'N/A' },// From Column J: PROVINCE
            { data: 'cityMunicipality', defaultContent: 'N/A' }, // From Column K: CITY/MUNICIPALITY
            {
                data: 'connectivityStatus',               // From Column Q: CONNECTIVITY STATUS
                render: function (data, type, row) {
                    let val = (data || '').toUpperCase();
                    let badge = val === 'ACTIVE' ? 'success' : 'danger';
                    let statusBadge = `<span class="badge badge-${badge}">${val}</span>`;

                    if (row.newThisQuarter) {
                        statusBadge += ' <span class="badge badge-info ml-1">New This Quarter</span>';
                    }

                    return statusBadge;
                }
            },
            { data: 'internetServiceProvider', defaultContent: 'N/A' },     // From Column R: INTERNET SERVICE PROVIDER
            { data: 'typeOfConnection', defaultContent: 'N/A' }, // From Column S: TYPE OF CONNECTION
            { data: 'speed', render: d => d ? d + ' Mbps' : 'N/A' }, // From Column T: SPEED(MBPS)
            { data: 'postmaster', defaultContent: 'N/A' }, // From Column C: Postmaster
            {
                data: null,
                render: (d, t, row) => {
                    const isSystemAdmin = $('body').data('is-system-admin') === true;
                    const isAreaAdmin = $('body').data('is-area-admin') === true;
                    const isAnyAdmin = $('body').data('is-any-admin') === true;

                    let buttons = `
                    <button class="btn btn-sm btn-primary view-btn" data-id="${row.id}">
                        <i class="fas fa-eye"></i>
                    </button>
                    `;

                    if (isAnyAdmin) {
                        buttons += `
                        <button class="btn btn-sm btn-warning edit-btn" data-id="${row.id}">
                            <i class="fas fa-edit"></i>
                        </button>
                        `;
                    }

                    // Only show Archive button for System Admin and Area Admin
                    if (isSystemAdmin || isAreaAdmin) {
                        buttons += `
                        <button class="btn btn-sm btn-archive-report" style="background:#fd7e14;color:white;"
                                data-id="${row.id}" data-name="${row.name || 'Office'}" title="Archive Office">
                            <i class="fas fa-archive"></i>
                        </button>
                        `;
                    }

                    return buttons;
                }
            }
        ],
        pageLength: 10,
        responsive: true,
        searching: false,
        order: [[1, 'asc']],
        initComplete: function () {
            // Table initialization complete
        }
    });

    // Delegated button events
    $('#postOfficeTable').on('click', 'tbody tr', function (e) {
        // Don't trigger if clicking on buttons or links
        if ($(e.target).closest('button, a').length > 0) {
            return;
        }

        const row = $(this).closest('tr');
        const rowData = $('#postOfficeTable').DataTable().row(row).data();
        if (rowData && rowData.id) {
            viewOfficeFromReport(rowData.id, rowData.name || 'Office');
        }
    });

    $('#postOfficeTable').on('click', '.view-btn', function () {
        viewOfficeFromReport($(this).data('id'));
    });

    $('#postOfficeTable').on('click', '.edit-btn', function () {
        editOfficeFromReport($(this).data('id'));
    });

    $('#postOfficeTable').on('click', '.btn-archive-report', function () {
        archiveOfficeFromReport($(this).data('id'), $(this).data('name'));
    });

    // Archive modal confirm button
    $('#reportConfirmArchiveBtn').on('click', function () {
        const id = $('#reportArchiveModal').data('office-id');
        const name = $('#reportArchiveOfficeName').text();
        const reason = $('#reportArchiveReasonInput').val().trim();

        $('#reportArchiveModal').modal('hide');
        performReportArchive(id, name, reason);
    });
}

function viewOfficeFromReport(id, name) {
    sessionStorage.setItem('reportReturnUrl', window.location.href);
    window.location.href = '/profile/' + id + '?source=report';
}

function editOfficeFromReport(id) {
    // Edit functionality - redirect to edit page or open edit modal
    window.location.href = '/edit/' + id;
}

function archiveOfficeFromReport(id, name) {
    $('#reportArchiveOfficeName').text(name);
    $('#reportArchiveReasonInput').val('');
    $('#reportArchiveModal').data('office-id', id);
    $('#reportArchiveModal').modal('show');
}

function performReportArchive(id, name, reason) {
    $.ajax({
        url: '/api/post-offices/' + id + '/archive',
        method: 'POST',
        data: JSON.stringify({ reason: reason }),
        contentType: 'application/json',
        success: function (response) {
            if (typeof Swal !== 'undefined') {
                Swal.fire({
                    icon: 'success',
                    title: 'Archived Successfully',
                    text: name + ' has been archived.',
                    confirmButtonColor: '#002868'
                });
            } else {
                alert(name + ' has been archived.');
            }
            // Reload the table
            $('#postOfficeTable').DataTable().ajax.reload();
        },
        error: function (xhr) {
            const errorMsg = xhr.responseJSON?.message || 'Failed to archive office. Please try again.';
            if (typeof Swal !== 'undefined') {
                Swal.fire({
                    icon: 'error',
                    title: 'Archive Failed',
                    text: errorMsg,
                    confirmButtonColor: '#002868'
                });
            } else {
                alert('Error: ' + errorMsg);
            }
        }
    });
}

function highlightActiveSelects() {
    $('#yearSelector, #quarterFilter, #areaFilter, #statusFilter').each(function () {
        $(this).toggleClass('has-value', !!$(this).val());
    });
}

function clearFilters() {
    $('#yearSelector').val('');
    $('#quarterFilter').val('');
    if ($('#areaFilter').length) $('#areaFilter').val('');
    $('#statusFilter').val('');
    highlightActiveSelects();
    renderFilterTags();
    window.location.replace('/report');
}