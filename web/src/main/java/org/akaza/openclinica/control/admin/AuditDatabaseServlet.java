/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: https://libreclinica.org/license
 * copyright (C) 2003 - 2011 Akaza Research
 * copyright (C) 2003 - 2019 OpenClinica
 * copyright (C) 2020 - 2024 LibreClinica
 */
package org.akaza.openclinica.control.admin;

import static org.jmesa.facade.TableFacadeFactory.createTableFacade;
import static org.akaza.openclinica.lctable.LCTableColumnDef.textCol;
import static org.akaza.openclinica.lctable.LCTableFilterDef.textFilter;
import static org.akaza.openclinica.lctable.LCTableParams.PARAM_MAX_ROWS;
import static org.akaza.openclinica.lctable.LCTableParams.PARAM_PAGE;
import static org.akaza.openclinica.lctable.LCTableParams.PARAM_SHOW_HIDDEN_COLS;
import static org.akaza.openclinica.lctable.LCTableParams.PARAM_SORT_DIR;
import static org.akaza.openclinica.lctable.LCTableParams.PARAM_SORT_PROP;
import static org.akaza.openclinica.lctable.LCTableText.key;
import static org.akaza.openclinica.lctable.LCTableUtil.NO_HX_TRIGGER;
import static org.akaza.openclinica.lctable.LCTableUtil.TIMESTAMP_FILTER_FOR_HTML_VALIDATION;
import static org.akaza.openclinica.lctable.LCTableUtil.TIMESTAMP_FILTER_MESSAGE;
import static org.akaza.openclinica.lctable.LCTableUtil.hxGetAttrs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.akaza.openclinica.control.SpringServletAccess;
import org.akaza.openclinica.control.core.SecureController;
import org.akaza.openclinica.dao.hibernate.DatabaseChangeLogDao;
import org.akaza.openclinica.domain.technicaladmin.DatabaseChangeLogBean;
import org.akaza.openclinica.i18n.core.LocaleResolver;
import org.akaza.openclinica.lctable.LCTable;
import org.akaza.openclinica.lctable.LCTableColumnDef;
import org.akaza.openclinica.lctable.LCTableContext;
import org.akaza.openclinica.lctable.LCTableData;
import org.akaza.openclinica.lctable.LCTableParams;
import org.akaza.openclinica.lctable.LCTableUtil;
import org.akaza.openclinica.view.Page;
import org.akaza.openclinica.web.InsufficientPermissionException;
import org.jmesa.facade.TableFacade;
import org.jmesa.view.editor.DateCellEditor;
import org.jmesa.view.html.component.HtmlColumn;
import org.jmesa.view.html.component.HtmlRow;
import org.jmesa.view.html.component.HtmlTable;
import org.xmlet.htmlapifaster.Div;

/**
 * Servlet for creating a user account.
 *
 * @author Krikor Krumlian
 */
public class AuditDatabaseServlet extends SecureController {

    private static final long serialVersionUID = 1L;
    private static final String TABLE_NAME = "databaseChangeLogs";

    // < ResourceBundle restext;
    Locale locale;
    private DatabaseChangeLogDao databaseChangeLogDao;

    /*
     * (non-Javadoc)
     * @see org.akaza.openclinica.control.core.SecureController#mayProceed()
     */
    @Override
    protected void mayProceed() throws InsufficientPermissionException {

        locale = LocaleResolver.getLocale(request);
        // < restext =
        // ResourceBundle.getBundle("org.akaza.openclinica.i18n.notes",locale);

        if (!ub.isSysAdmin()) {
            throw new InsufficientPermissionException(Page.MENU, resexception.getString("you_may_not_perform_administrative_functions"), "1");
        }

        return;
    }

    @Override
    protected void processRequest() throws Exception {
        List<DatabaseChangeLogBean> databaseChangeLogs = getDatabaseChangeLogDao().findAll();
        String lcTableRendering = System.getenv("LC_TABLE_RENDERING");
        if (lcTableRendering != null && lcTableRendering.equalsIgnoreCase("jmesa")) {
            request.setAttribute("tableRenderingMode", "jmesa");
            request.setAttribute("auditDatabaseHtml", renderAuditDatabaseTable(databaseChangeLogs));
            forwardPage(Page.AUDIT_DATABASE);
        } else {
            request.setAttribute("tableRenderingMode", "htmlflow");
            String auditDatabaseHtml = renderAuditDatabaseLCTable(databaseChangeLogs);
            response.addHeader("Vary", "HX-Request");
            if (request.getHeader("HX-Request") != null) {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(auditDatabaseHtml);
                response.getWriter().flush();
            } else {
                request.setAttribute("auditDatabaseHtml", auditDatabaseHtml);
                forwardPage(Page.AUDIT_DATABASE);
            }
        }

    }

    String renderAuditDatabaseLCTable(List<DatabaseChangeLogBean> databaseChangeLogs) {
        List<LCTableColumnDef<DatabaseChangeLogBean>> columns = List.of(
            textCol("id",           key("Id"),            "id",            0, DatabaseChangeLogBean::getId),
            textCol("author",       key("author"),        "author",        0, DatabaseChangeLogBean::getAuthor),
            textCol("fileName",     key("file_name"),     "file-name",     0, DatabaseChangeLogBean::getFileName),
            textCol("dataExecuted", key("date_executed"), "date-executed", 0,
                textFilter(TIMESTAMP_FILTER_FOR_HTML_VALIDATION, TIMESTAMP_FILTER_MESSAGE),
                DatabaseChangeLogBean::getDataExecuted, LCTableUtil::timestampToString),
            textCol("md5Sum",       key("md5_sum"),       "md5-sum",       0, DatabaseChangeLogBean::getMd5Sum),
            textCol("description",  key("description"),   "description",   0, DatabaseChangeLogBean::getDescription),
            textCol("comments",     key("comments"),      "comments",      0, DatabaseChangeLogBean::getComments),
            textCol("tag",          key("tag"),           "tag",           0, DatabaseChangeLogBean::getTag),
            textCol("liquibase",    key("liquibase"),     "liquibase",     0, DatabaseChangeLogBean::getLiquibase)
        );
        LCTable<DatabaseChangeLogBean> table = new LCTable<>(TABLE_NAME, columns,
            params -> fetchAuditDatabaseData(databaseChangeLogs, params));
        table.addCustomToolbarControl(this::renderClearFilterControl);

        LCTableParams params = new LCTableParams(request.getQueryString(), table);
        return table.render(request.getRequestURI(), params, request.getContextPath(), LocaleResolver.getLocale(request));
    }

    private LCTableData<DatabaseChangeLogBean> fetchAuditDatabaseData(
            List<DatabaseChangeLogBean> databaseChangeLogs, LCTableParams params) {
        List<DatabaseChangeLogBean> filteredRows = new ArrayList<>();
        for (DatabaseChangeLogBean row : databaseChangeLogs) {
            if (matchesFilters(row, params.filters)) {
                filteredRows.add(row);
            }
        }

        Comparator<DatabaseChangeLogBean> comparator = comparatorFor(params.sortProp);
        if (comparator != null) {
            if ("desc".equalsIgnoreCase(params.sortDir)) {
                comparator = comparator.reversed();
            }
            filteredRows.sort(comparator);
        }

        int fromIndex = Math.min(params.page * params.maxRows, filteredRows.size());
        int toIndex = Math.min(fromIndex + params.maxRows, filteredRows.size());
        return new LCTableData<>(new ArrayList<>(filteredRows.subList(fromIndex, toIndex)), filteredRows.size());
    }

    private boolean matchesFilters(DatabaseChangeLogBean row, Map<String, String> filters) {
        Map<String, String> values = new HashMap<>();
        values.put("id", row.getId());
        values.put("author", row.getAuthor());
        values.put("fileName", row.getFileName());
        values.put("dataExecuted", row.getDataExecuted() == null ? null : LCTableUtil.timestampToString(row.getDataExecuted()));
        values.put("md5Sum", row.getMd5Sum());
        values.put("description", row.getDescription());
        values.put("comments", row.getComments());
        values.put("tag", row.getTag());
        values.put("liquibase", row.getLiquibase());

        return filters.entrySet().stream().allMatch(filter -> {
            String value = values.get(filter.getKey());
            return value != null && value.toLowerCase(Locale.ROOT).contains(filter.getValue().toLowerCase(Locale.ROOT));
        });
    }

    private Comparator<DatabaseChangeLogBean> comparatorFor(String property) {
        if ("id".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getId);
        }
        if ("author".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getAuthor);
        }
        if ("fileName".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getFileName);
        }
        if ("dataExecuted".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getDataExecuted);
        }
        if ("md5Sum".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getMd5Sum);
        }
        if ("description".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getDescription);
        }
        if ("comments".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getComments);
        }
        if ("tag".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getTag);
        }
        if ("liquibase".equals(property)) {
            return comparingNullable(DatabaseChangeLogBean::getLiquibase);
        }
        return null;
    }

    private static <U extends Comparable<? super U>> Comparator<DatabaseChangeLogBean> comparingNullable(
            Function<DatabaseChangeLogBean, U> extractor) {
        return Comparator.comparing(extractor, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private void renderClearFilterControl(Div<?> container, LCTableContext<DatabaseChangeLogBean> context) {
        String nonFilterParams = "[name=" + PARAM_PAGE + "]"
            + ",[name=" + PARAM_MAX_ROWS + "]"
            + ",[name=" + PARAM_SORT_PROP + "]"
            + ",[name=" + PARAM_SORT_DIR + "]"
            + ",[name=" + PARAM_SHOW_HIDDEN_COLS + "]";
        container.a()
            .attrId(TABLE_NAME + "-clear-filter-toolbar")
            .attrClass("text-btn")
            .addAttr("data-testid", "clear-filter-button")
            .addAttr("data-test-action", "clear-filter")
            .of(hxGetAttrs(context.entityPath, nonFilterParams, "#" + TABLE_NAME + "-panel", NO_HX_TRIGGER))
            .text(context.words.getString("table_clear_filter"))
            .__();
    }

    private String renderAuditDatabaseTable(List<DatabaseChangeLogBean> databaseChangeLogs) {

        // Collection<StudyRowContainer> items = getStudyRows(studyBeans);
        TableFacade tableFacade = createTableFacade("databaseChangeLogs", request);
        tableFacade.setColumnProperties("id", "author", "fileName", "dataExecuted", "md5Sum", "description", "comments", "tag", "liquibase");

        tableFacade.setItems(databaseChangeLogs);
        // Fix column titles
        HtmlTable table = (HtmlTable) tableFacade.getTable();

        table.setCaption("");
        HtmlRow row = table.getRow();

        HtmlColumn id = row.getColumn("id");
        id.setTitle("Id");

        HtmlColumn author = row.getColumn("author");
        author.setTitle("Author");

        HtmlColumn fileName = row.getColumn("fileName");
        fileName.setTitle("File Name");

        HtmlColumn dataExecuted = row.getColumn("dataExecuted");
        dataExecuted.setTitle("Date Executed");
        dataExecuted.getCellRenderer().setCellEditor(new DateCellEditor("yyyy-MM-dd hh:mm:ss"));

        HtmlColumn md5Sum = row.getColumn("md5Sum");
        md5Sum.setTitle("md5 sum");

        HtmlColumn description = row.getColumn("description");
        description.setTitle("Description");

        HtmlColumn comments = row.getColumn("comments");
        comments.setTitle("Comments");

        HtmlColumn tag = row.getColumn("tag");
        tag.setTitle("Tag");

        HtmlColumn liquibase = row.getColumn("liquibase");
        liquibase.setTitle("Liquibase");

        return tableFacade.render();
    }

    @Override
    protected String getAdminServlet() {
        return SecureController.ADMIN_SERVLET_CODE;
    }

    public DatabaseChangeLogDao getDatabaseChangeLogDao() {
        databaseChangeLogDao =
            this.databaseChangeLogDao != null ? databaseChangeLogDao : (DatabaseChangeLogDao) SpringServletAccess.getApplicationContext(context).getBean(
                    "databaseChangeLogDao");
        return databaseChangeLogDao;
    }

    public void setDatabaseChangeLogDao(DatabaseChangeLogDao databaseChangeLogDao) {
        this.databaseChangeLogDao = databaseChangeLogDao;
    }

}
