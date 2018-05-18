package io.jenkins.plugins.analysis.core.model;

import java.util.Locale;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.analysis.Issue;
import edu.hm.hafner.analysis.IssueBuilder;
import edu.hm.hafner.analysis.Priority;
import edu.hm.hafner.analysis.Report;
import edu.hm.hafner.util.ResourceTest;
import static io.jenkins.plugins.analysis.core.model.Assertions.assertThat;
import io.jenkins.plugins.analysis.core.model.StaticAnalysisLabelProvider.AgeBuilder;
import io.jenkins.plugins.analysis.core.model.StaticAnalysisLabelProvider.DefaultAgeBuilder;
import static io.jenkins.plugins.analysis.core.testutil.Assertions.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Tests the class {@link StaticAnalysisLabelProvider}.
 *
 * @author Ullrich Hafner
 */
class StaticAnalysisLabelProviderTest {
    private static final String ID = "id";
    private static final String NAME = "name";

    @Test
    void shouldReturnIdAndNameOfConstructorParametersInAllDisplayProperties() {
        StaticAnalysisLabelProvider labelProvider = new StaticAnalysisLabelProvider(ID, NAME);

        assertThat(labelProvider).hasId(ID);
        assertThat(labelProvider).hasName(NAME);
        assertThat(labelProvider.getLinkName()).contains(NAME);
        assertThat(labelProvider.getTrendName()).contains(NAME);
        assertThat(labelProvider.getResultUrl()).contains(ID);
    }

    @Test
    void shouldReturnIdAndDefaultNameIfNoNameIsGiven() {
        StaticAnalysisLabelProvider emptyNameLabelProvider = new StaticAnalysisLabelProvider(ID, "");

        assertThat(emptyNameLabelProvider).hasId(ID);
        assertThat(emptyNameLabelProvider).hasName(emptyNameLabelProvider.getDefaultName());

        StaticAnalysisLabelProvider nullNameLabelProvider = new StaticAnalysisLabelProvider(ID, null);

        assertThat(nullNameLabelProvider).hasId(ID);
        assertThat(nullNameLabelProvider).hasName(nullNameLabelProvider.getDefaultName());

        StaticAnalysisLabelProvider noNameLabelProvider = new StaticAnalysisLabelProvider(ID);

        assertThat(noNameLabelProvider).hasId(ID);
        assertThat(noNameLabelProvider).hasName(noNameLabelProvider.getDefaultName());
    }

    private void assertThatColumnsAreValid(final JSONArray columns, int index) {
        assertThat(columns.get(0)).isEqualTo(
                "<div class=\"details-control\" data-description=\"&lt;p&gt;&lt;strong&gt;MESSAGE&lt;/strong&gt;&lt;/p&gt; DESCRIPTION\"></div>");
        String actual = columns.getString(1);
        assertThat(actual).matches(createFileLinkMatcher("file-" + index, 15));
        assertThat(columns.get(2)).isEqualTo(createPropertyLink("packageName", "package-" + index));
        assertThat(columns.get(3)).isEqualTo(createPropertyLink("category", "category-" + index));
        assertThat(columns.get(4)).isEqualTo(createPropertyLink("type", "type-" + index));
        assertThat(columns.get(5)).isEqualTo("<a href=\"HIGH\">High</a>");
        assertThat(columns.get(6)).isEqualTo("1");
    }

    private String createPropertyLink(final String property, final String value) {
        return String.format("<a href=\"%s.%d/\">%s</a>", property, value.hashCode(), value);
    }

    private String createFileLinkMatcher(final String fileName, final int lineNumber) {
        return "<a href=\\\"source.[0-9a-f-]+/#" + lineNumber + "\\\">"
                + fileName + ":" + lineNumber
                + "</a>";
    }

    private IssueBuilder createBuilder() {
        return new IssueBuilder().setMessage("MESSAGE").setDescription("DESCRIPTION");
    }

    /**
     * Tests the class {@link AgeBuilder}.
     */
    @Nested
    class AgeBuilderTest {
        @Test
        void shouldCreateAgeLinkForFirstBuild() {
            AgeBuilder builder = new DefaultAgeBuilder(1, "checkstyleResult/");

            assertThat(builder.apply(1)).isEqualTo("1");
        }

        @Test
        void shouldCreateAgeLinkForPreviousBuilds() {
            AgeBuilder builder = new DefaultAgeBuilder(10, "checkstyleResult/");
            assertThat(builder.apply(1))
                    .isEqualTo("<a href=\"../../1/checkstyleResult\">10</a>");
            assertThat(builder.apply(9))
                    .isEqualTo("<a href=\"../../9/checkstyleResult\">2</a>");
            assertThat(builder.apply(10))
                    .isEqualTo("1");
        }

        @Test
        void shouldCreateAgeLinkForSubDetails() {
            AgeBuilder builder = new DefaultAgeBuilder(10, "checkstyleResult/package.1234/");
            assertThat(builder.apply(1))
                    .isEqualTo("<a href=\"../../../1/checkstyleResult\">10</a>");
            assertThat(builder.apply(9))
                    .isEqualTo("<a href=\"../../../9/checkstyleResult\">2</a>");
            assertThat(builder.apply(10))
                    .isEqualTo("1");
        }
    }

    /**
     * Tests the dynamic creation of the table model of a {@link Report}, i.e. a list of {@link Issue} instances.
     */
    @Nested
    class TableModelTest {
        @Test
        void shouldConvertIssuesToJsonArray() {
            Locale.setDefault(Locale.ENGLISH);

            Report report = new Report();
            report.add(createIssue(1));

            StaticAnalysisLabelProvider labelProvider = new StaticAnalysisLabelProvider();
            JSONObject oneElement = labelProvider.toJsonArray(report, new DefaultAgeBuilder(1, "url"));

            assertThatJson(oneElement).node("data").isArray().ofLength(1);

            JSONArray singleRow = getDataSection(oneElement);

            JSONArray columns = singleRow.getJSONArray(0);

            assertThatColumnsAreValid(columns, 1);

            report.add(createIssue(2));
            JSONObject twoElements = labelProvider.toJsonArray(report, new DefaultAgeBuilder(1, "url"));

            assertThatJson(twoElements).node("data").isArray().ofLength(2);

            JSONArray rows = getDataSection(twoElements);

            JSONArray columnsFirstRow = rows.getJSONArray(0);
            assertThatColumnsAreValid(columnsFirstRow, 1);

            assertThatJson(rows.get(1)).isArray().ofLength(IssueModelTest.EXPECTED_NUMBER_OF_COLUMNS);
            JSONArray columnsSecondRow = rows.getJSONArray(1);
            assertThatColumnsAreValid(columnsSecondRow, 2);
        }

        private JSONArray getDataSection(final JSONObject oneElement) {
            JSONArray singleRow = oneElement.getJSONArray("data");
            assertThatJson(singleRow.get(0)).isArray().ofLength(IssueModelTest.EXPECTED_NUMBER_OF_COLUMNS);
            return singleRow;
        }

        private Issue createIssue(final int index) {
            IssueBuilder builder = createBuilder();
            builder.setFileName("/path/to/file-" + index)
                    .setPackageName("package-" + index)
                    .setCategory("category-" + index)
                    .setType("type-" + index)
                    .setLineStart(15)
                    .setPriority(Priority.HIGH)
                    .setReference("1");
            return builder.build();
        }
    }

    /**
     * Tests the dynamic creation of the table model for a single {@link Issue}.
     */
    @Nested
    class IssueModelTest extends ResourceTest {
        static final int EXPECTED_NUMBER_OF_COLUMNS = 7;

        @Test
        void shouldConvertIssueToArrayOfColumns() {
            Locale.setDefault(Locale.ENGLISH);

            IssueBuilder builder = createBuilder();
            Issue issue = builder.setFileName("path/to/file-1")
                    .setPackageName("package-1")
                    .setCategory("category-1")
                    .setType("type-1")
                    .setLineStart(15)
                    .setPriority(Priority.HIGH)
                    .setReference("1").build();

            StaticAnalysisLabelProvider provider = new StaticAnalysisLabelProvider();
            JSONArray columns = provider.toJson(issue, build -> String.valueOf(build));

            assertThatJson(columns).isArray().ofLength(EXPECTED_NUMBER_OF_COLUMNS);
            assertThatColumnsAreValid(columns, 1);
        }
    }
}