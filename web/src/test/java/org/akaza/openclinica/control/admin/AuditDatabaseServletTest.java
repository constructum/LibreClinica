/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).
 *
 * For details see: https://libreclinica.org/license
 * copyright (C) 2026 LibreClinica
 */
package org.akaza.openclinica.control.admin;

import junit.framework.TestCase;
import org.akaza.openclinica.domain.technicaladmin.DatabaseChangeLogBean;
import org.akaza.openclinica.i18n.util.ResourceBundleProvider;
import org.akaza.openclinica.lctable.LCTableUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuditDatabaseServletTest extends TestCase {

    @Override
    protected void setUp() {
        ResourceBundleProvider.updateLocale(Locale.ENGLISH);
    }

    public void testRendersLocalizedColumnsTimestampValidationAndToolbarClearFilter() {
        DatabaseChangeLogBean row = row("001", "alice", "db/changelog.xml", new Date(0L),
            "9:abc", "create table", "initial schema", "release-1", "4.27");
        TestAuditDatabaseServlet servlet = servlet(null);

        String html = servlet.render(Collections.singletonList(row));

        assertColumn(html, "id", "Id");
        assertColumn(html, "author", "Author");
        assertColumn(html, "file-name", "File Name");
        assertColumn(html, "date-executed", "Date Executed");
        assertColumn(html, "md5-sum", "md5 sum");
        assertColumn(html, "description", "Description");
        assertColumn(html, "comments", "Comments");
        assertColumn(html, "tag", "Tag");
        assertColumn(html, "liquibase", "Liquibase");
        assertTrue(html.contains(LCTableUtil.timestampToString(row.getDataExecuted())));
        assertTrue(html.contains("pattern=\"" + LCTableUtil.TIMESTAMP_FILTER_FOR_HTML_VALIDATION + "\""));
        assertTrue(html.contains("id=\"databaseChangeLogs-clear-filter-toolbar\""));
        assertTrue(html.contains("data-test-action=\"clear-filter\""));
        assertTrue(html.contains("Clear Filter"));
        assertTrue(html.indexOf("data-testid=\"lctable-toolbar\"") < html.indexOf("Clear Filter"));
    }

    public void testFiltersSortsAndPaginatesInMemoryRows() {
        DatabaseChangeLogBean alpha = row("001", "Alice", "alpha.xml", new Date(1_000L),
            "a", "first", "keep", "v1", "4.0");
        DatabaseChangeLogBean beta = row("002", "Bob", "beta.xml", new Date(2_000L),
            "b", "second", "keep", "v2", "4.1");
        DatabaseChangeLogBean gamma = row("003", "Carol", "gamma.xml", new Date(3_000L),
            "c", "third", "drop", "v3", "4.2");
        TestAuditDatabaseServlet servlet = servlet(
            "q.comments=keep&sortProp=author&sortDir=desc&maxRows=1&page=2");

        String html = servlet.render(Arrays.asList(alpha, beta, gamma));

        assertTrue(html.contains("Alice"));
        assertFalse(html.contains("Bob"));
        assertFalse(html.contains("Carol"));
        assertTrue(html.contains("name=\"q.comments\" value=\"keep\""));
        assertTrue(html.contains("data-test-sort=\"desc\""));
    }

    private static TestAuditDatabaseServlet servlet(String queryString) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getQueryString()).thenReturn(queryString);
        when(request.getRequestURI()).thenReturn("/AuditDatabase");
        when(request.getContextPath()).thenReturn("");
        when(request.getLocales()).thenReturn(Collections.enumeration(Collections.singletonList(Locale.ENGLISH)));

        TestAuditDatabaseServlet servlet = new TestAuditDatabaseServlet();
        servlet.setRequest(request);
        servlet.locale = Locale.ENGLISH;
        return servlet;
    }

    private static DatabaseChangeLogBean row(String id, String author, String fileName, Date dataExecuted,
            String md5Sum, String description, String comments, String tag, String liquibase) {
        DatabaseChangeLogBean row = new DatabaseChangeLogBean();
        row.setId(id);
        row.setAuthor(author);
        row.setFileName(fileName);
        row.setDataExecuted(dataExecuted);
        row.setMd5Sum(md5Sum);
        row.setDescription(description);
        row.setComments(comments);
        row.setTag(tag);
        row.setLiquibase(liquibase);
        return row;
    }

    private static void assertColumn(String html, String testName, String displayName) {
        assertTrue("Missing data-test-column=" + testName,
            html.contains("data-test-column=\"" + testName + "\""));
        assertTrue("Missing localized column title " + displayName,
            html.contains(displayName));
    }

    private static final class TestAuditDatabaseServlet extends AuditDatabaseServlet {
        private void setRequest(HttpServletRequest request) {
            this.request = request;
        }

        private String render(List<DatabaseChangeLogBean> rows) {
            return renderAuditDatabaseLCTable(rows);
        }
    }
}
