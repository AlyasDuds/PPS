/**
 * archive.js
 *
 * Handles all archive / restore interactions:
 *   - Archive button on table.html  (opens reason modal → POST /api/archive/{id})
 *   - Archive inbox on archive.html   (DataTable + restore / bulk restore)
 *   - Select-all checkbox on archive.html
 *
 * Requires: jQuery, SweetAlert2, Bootstrap 4, DataTables 2.x
 */
let archiveTable = null;

$(document).ready(function () {
    console.log('Archive.js loaded and ready!');

    // ── Helpers ──────────────────────────────────────────────────────────────

    const Toast = Swal.mixin({
        toast: true,
        position: 'top-end',
        showConfirmButton: false,
        timer: 3000,
        timerProgressBar: true
    });

    function showSuccess(msg) {
        Toast.fire({ icon: 'success', title: msg });
    }

    function showError(msg) {
        Toast.fire({ icon: 'error', title: msg });
    }

    // ── ARCHIVE from table.html ───────────────────────────────────────────────

    let pendingArchiveId   = null;
    let pendingArchiveRow  = null;

    $(document).on('click', '.btn-archive', function (e) {
        e.preventDefault();
        e.stopPropagation();

        pendingArchiveId  = $(this).data('office-id');
        pendingArchiveRow = $(this).closest('tr');
        const name        = $(this).data('office-name');

        const $modal = $('#archiveReasonModal');
        if ($modal.length === 0) {
            console.error('Archive modal not found in DOM!');
            alert('Error: Archive modal not found. Please refresh the page.');
            return;
        }

        $('#archiveOfficeName').text(name);
        $('#archiveReasonInput').val('');
        $('#archiveDateInput').val('');

        try {
            if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                const modal = new bootstrap.Modal($modal[0]);
                modal.show();
            } else if (typeof $.fn.modal === 'function') {
                $modal.modal('show');
            } else {
                $modal.addClass('show').css('display', 'block');
                $('body').addClass('modal-open');
                $('<div class="modal-backdrop fade show"></div>').insertAfter($modal);
            }
        } catch (error) {
            console.error('Error showing modal:', error);
            alert('Error showing archive modal: ' + error.message);
        }
    });

    $(document).on('click', '.btn-archive i', function (e) {
        e.preventDefault();
        e.stopPropagation();
        const $button = $(this).parent('.btn-archive');
        if ($button.length) {
            $button.trigger('click');
        }
    });

    $('#confirmArchiveBtn').on('click', function () {
        if (!pendingArchiveId) return;

        const reason = $('#archiveReasonInput').val().trim();
        const archiveDate = $('#archiveDateInput').val();
        const $btn   = $(this);

        $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin mr-1"></i> Archiving...');

        const requestData = { reason: reason };
        if (archiveDate) {
            requestData.archiveDate = archiveDate;
        }

        $.ajax({
            url: '/api/archive/' + pendingArchiveId,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(requestData),
            success: function (res) {
                $('#archiveReasonModal').modal('hide');
                $btn.prop('disabled', false).html('<i class="fas fa-archive mr-1"></i> Archive');

                if (res.success) {
                    const path = window.location.pathname;
                    if (path.includes('/dashboard') || path.includes('/table') || path.includes('/quarters')) {
                        showSuccess(res.message);
                        setTimeout(() => location.reload(), 1200);
                    } else {
                        if (pendingArchiveRow) pendingArchiveRow.fadeOut(400, function () { $(this).remove(); });
                        showSuccess(res.message);
                    }
                } else {
                    showError(res.message || 'Archive failed.');
                }
            },
            error: function (xhr) {
                $btn.prop('disabled', false).html('<i class="fas fa-archive mr-1"></i> Archive');
                const msg = xhr.responseJSON?.message || 'An error occurred.';
                showError(msg);
            }
        });
    });

    // ── RESTORE single (archive.html) ─────────────────────────────────────────

    $(document).on('click', '.btn-restore', function () {
        const id   = $(this).data('office-id');
        const name = $(this).data('office-name');
        const $row = $(this).closest('tr');

        Swal.fire({
            icon: 'question',
            title: 'Restore Office?',
            html: `<strong>${name}</strong> will be returned to the active inventory as <em>Inactive</em>. You can re-activate it afterwards.`,
            showCancelButton: true,
            confirmButtonText: 'Restore',
            confirmButtonColor: '#28a745',
            cancelButtonText: 'Cancel'
        }).then(result => {
            if (!result.isConfirmed) return;

            $.ajax({
                url: '/api/restore/' + id,
                method: 'POST',
                success: function (res) {
                    if (res.success) {
                        removeArchiveRows($row, 1);
                        showSuccess(res.message);
                    } else {
                        showError(res.message || 'Restore failed.');
                    }
                },
                error: function (xhr) {
                    showError(xhr.responseJSON?.message || 'An error occurred.');
                }
            });
        });
    });

    // ── SELECT ALL (archive.html) ─────────────────────────────────────────────

    $('#selectAll').on('change', function () {
        const checked = this.checked;
        setAllRowCheckboxes(checked);
        $('#selectAll').prop('indeterminate', false);
        refreshBulkBar();
    });

    $(document).on('change', '.row-checkbox', function () {
        syncSelectAllState();
        refreshBulkBar();
    });

    function setAllRowCheckboxes(checked) {
        if (archiveTable) {
            archiveTable.rows({ search: 'applied' }).every(function () {
                $(this.node()).find('.row-checkbox').prop('checked', checked);
            });
        } else {
            $('.row-checkbox').prop('checked', checked);
        }
    }

    function syncSelectAllState() {
        const boxes = archiveTable
            ? archiveTable.rows({ search: 'applied' }).nodes().to$().find('.row-checkbox')
            : $('.row-checkbox');
        const total   = boxes.length;
        const checked = boxes.filter(':checked').length;
        $('#selectAll').prop('indeterminate', checked > 0 && checked < total);
        $('#selectAll').prop('checked', total > 0 && checked === total);
    }

    function refreshBulkBar() {
        const count = archiveTable
            ? archiveTable.rows({ search: 'applied' }).nodes().to$().find('.row-checkbox:checked').length
            : $('.row-checkbox:checked').length;
        if (count > 0) {
            $('#selectedCount').text(count);
            $('#bulkActionBar').show();
        } else {
            $('#bulkActionBar').hide();
        }
    }

    $('#btnClearSelection').on('click', function () {
        setAllRowCheckboxes(false);
        $('#selectAll').prop({ checked: false, indeterminate: false });
        refreshBulkBar();
    });

    // ── BULK RESTORE (archive.html) ───────────────────────────────────────────

    $('#btnBulkRestore').on('click', function () {
        const ids = getCheckedOfficeIds();
        if (ids.length === 0) return;

        Swal.fire({
            icon: 'question',
            title: `Restore ${ids.length} office(s)?`,
            text: 'They will be returned to the active inventory as Inactive.',
            showCancelButton: true,
            confirmButtonText: 'Restore All',
            confirmButtonColor: '#28a745'
        }).then(result => {
            if (!result.isConfirmed) return;

            $.ajax({
                url: '/api/restore/bulk',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({ ids: ids }),
                success: function (res) {
                    if (res.success) {
                        const $rows = archiveTable
                            ? archiveTable.rows().nodes().to$().find('.row-checkbox:checked').closest('tr')
                            : $('.row-checkbox:checked').closest('tr');
                        removeArchiveRows($rows, ids.length);
                        $('#bulkActionBar').hide();
                        $('#selectAll').prop({ checked: false, indeterminate: false });
                        showSuccess(res.message);
                    } else {
                        showError(res.message || 'Bulk restore failed.');
                    }
                },
                error: function (xhr) {
                    showError(xhr.responseJSON?.message || 'An error occurred.');
                }
            });
        });
    });

    function getCheckedOfficeIds() {
        const source = archiveTable
            ? archiveTable.rows({ search: 'applied' }).nodes().to$().find('.row-checkbox:checked')
            : $('.row-checkbox:checked');
        return source.map(function () {
            return parseInt($(this).data('id'), 10);
        }).get().filter(id => !isNaN(id));
    }

    function removeArchiveRows($rows, delta) {
        if (archiveTable && $rows.length) {
            archiveTable.rows($rows).remove().draw(false);
            syncArchiveSummary();
        } else if ($rows.length) {
            $rows.fadeOut(400, function () { $(this).remove(); });
        }
        updateArchivedCount(-delta);
    }

    function updateArchivedCount(delta) {
        const selectors = ['#archivedCountStat', '#archivedCountDisplay'];
        selectors.forEach(sel => {
            const $el = $(sel);
            if ($el.length) {
                const current = parseInt($el.text(), 10) || 0;
                $el.text(Math.max(0, current + delta));
            }
        });
        syncArchiveSummary();
    }

    function syncArchiveSummary() {
        const $summary = $('#archiveSummary');
        if (!$summary.length) return;
        const total = archiveTable
            ? archiveTable.rows().count()
            : $('#archiveTable tbody tr').length;
        if (total === 0) {
            $summary.text('Inbox is empty');
        } else {
            $summary.text(total + (total === 1 ? ' office' : ' offices'));
        }
    }

    // ── Archive inbox DataTable (after deferred DataTables script loads) ───

    if (document.getElementById('archiveTable')) {
        $(window).on('load', initArchiveInbox);
    }
});

function initArchiveInbox() {
    const tableEl = document.getElementById('archiveTable');
    if (!tableEl || archiveTable) return;

    if (typeof DataTable === 'undefined') {
        console.warn('DataTables not available; archive inbox table not initialized.');
        syncArchiveSummaryStatic();
        return;
    }

    if ($.fn.DataTable && $.fn.DataTable.isDataTable('#archiveTable')) {
        $('#archiveTable').DataTable().destroy();
    }

    // ── Custom date-range filter ───────────────────────────────────────────
    $.fn.dataTable.ext.search.push(function (settings, data) {
        if (settings.nTable.id !== 'archiveTable') return true;
        const from = $('#archiveDateFrom').val();
        const to   = $('#archiveDateTo').val();
        if (!from && !to) return true;

        // Column 6 = "Archived On" — format: "Jan 01, 2025"
        const raw = data[6] || '';
        if (!raw) return false;
        const rowDate = new Date(raw);
        if (isNaN(rowDate.getTime())) return true;

        if (from && rowDate < new Date(from))  return false;
        if (to   && rowDate > new Date(to + 'T23:59:59')) return false;
        return true;
    });

    archiveTable = new DataTable('#archiveTable', {
        pageLength: 10,
        lengthMenu: [10, 25, 50, 100],
        order: [[6, 'desc']],
        dom: 'tip',           // hide default search box — we use our own
        language: {
            emptyTable: 'No archived post offices found.',
            lengthMenu: 'Show _MENU_ entries',
            info: 'Showing _START_ to _END_ of _TOTAL_ archived offices',
            infoEmpty: 'No archived offices',
            zeroRecords: 'No matching archived offices'
        },
        columnDefs: [
            { orderable: false, targets: [0, 9] }
        ],
        drawCallback: function () {
            syncArchiveSummaryStatic();
            $('#selectAll').prop({ checked: false, indeterminate: false });
            refreshBulkBarStatic();
        }
    });

    // ── Wire up filter inputs ─────────────────────────────────────────────
    $('#archiveSearchInput').on('input', function () {
        archiveTable.search(this.value).draw();
    });

    // Area filter — column 3
    $('#archiveAreaFilter').on('change', function () {
        archiveTable.column(3).search(this.value).draw();
    });

    // Date range
    $('#archiveDateFrom, #archiveDateTo').on('change', function () {
        archiveTable.draw();
    });

    // Clear all filters
    $('#archiveClearFilters').on('click', function () {
        $('#archiveSearchInput').val('');
        $('#archiveAreaFilter').val('');
        $('#archiveDateFrom').val('');
        $('#archiveDateTo').val('');
        archiveTable.search('').columns().search('').draw();
    });

    // ── Populate Area dropdown from API ───────────────────────────────────
    $.getJSON('/api/postal/areas').done(function (areas) {
        const $sel = $('#archiveAreaFilter');
        (areas || []).sort((a, b) => a.name.localeCompare(b.name)).forEach(function (area) {
            $sel.append($('<option>').val(area.name).text(area.name));
        });
    });

    syncArchiveSummaryStatic();
}

function syncArchiveSummaryStatic() {
    const $summary = $('#archiveSummary');
    if (!$summary.length) return;
    const total = archiveTable
        ? archiveTable.rows().count()
        : document.querySelectorAll('#archiveTable tbody tr').length;
    $summary.text(total === 0 ? 'Inbox is empty' : total + (total === 1 ? ' office' : ' offices'));
}

function refreshBulkBarStatic() {
    const count = archiveTable
        ? archiveTable.rows({ search: 'applied' }).nodes().to$().find('.row-checkbox:checked').length
        : document.querySelectorAll('.row-checkbox:checked').length;
    if (count > 0) {
        $('#selectedCount').text(count);
        $('#bulkActionBar').show();
    } else {
        $('#bulkActionBar').hide();
    }
}
