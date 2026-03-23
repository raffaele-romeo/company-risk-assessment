package com.tunicpay.riskassessment.datasource;

import com.tunicpay.riskassessment.model.CompanyProfile;
import com.tunicpay.riskassessment.model.Officer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnExpression("'${app.companies-house.api-key:}' == ''")
public class MockCompaniesHouseDataSource implements DataSource<CompaniesHouseData> {

    private static final Logger log = LoggerFactory.getLogger(MockCompaniesHouseDataSource.class);

    private static final Map<String, CompaniesHouseData> FIXTURES = Map.of(
            "12345678", buildTunicPay(),
            "11044276", buildAcmeConsulting(),
            "99887766", buildGlobexCorp()
    );

    public MockCompaniesHouseDataSource() {
        log.info("Using MOCK Companies House data source (no API key configured)");
    }

    @Override
    public String name() {
        return "companies-house";
    }

    @Override
    public DataSourceResult<CompaniesHouseData> fetch(String companyNumber, String companyName, String jurisdiction) {
        long start = System.currentTimeMillis();
        log.info("Mock fetch for company {}", companyNumber);

        CompaniesHouseData data = FIXTURES.get(companyNumber);
        if (data == null) {
            data = buildDefault(companyNumber, companyName);
        }

        long duration = System.currentTimeMillis() - start;
        return DataSourceResult.success(data, name(), duration);
    }

    private static CompaniesHouseData buildTunicPay() {
        return new CompaniesHouseData(
                new CompanyProfile("TUNIC PAY LTD", "12345678", "active", "2020-03-15", null,
                        "10 Finsbury Square, London, EC2A 1AF"),
                List.of(
                        new Officer("SMITH, John", "director", "2020-03-15", null),
                        new Officer("JONES, Sarah", "secretary", "2020-06-01", "2022-12-31"),
                        new Officer("BROWN, David", "director", "2021-01-10", null)
                ),
                List.of(
                        new CompaniesHouseData.RawAccountFiling("2025-03-10", "2024-12-31", "AA"),
                        new CompaniesHouseData.RawAccountFiling("2024-04-15", "2023-12-31", "AA"),
                        new CompaniesHouseData.RawAccountFiling("2023-11-20", "2022-12-31", "AA"),
                        new CompaniesHouseData.RawAccountFiling("2022-06-30", "2021-12-31", "AA")
                ),
                List.of(
                        new CompaniesHouseData.RawConfirmationStatementFiling("2025-03-20", "2025-03-15", "CS01"),
                        new CompaniesHouseData.RawConfirmationStatementFiling("2024-03-18", "2024-03-15", "CS01"),
                        new CompaniesHouseData.RawConfirmationStatementFiling("2023-03-20", "2023-03-15", "CS01"),
                        new CompaniesHouseData.RawConfirmationStatementFiling("2022-03-16", "2022-03-15", "CS01"),
                        new CompaniesHouseData.RawConfirmationStatementFiling("2021-03-25", "2021-03-15", "CS01")
                ),
                List.of()
        );
    }

    private static CompaniesHouseData buildAcmeConsulting() {
        return new CompaniesHouseData(
                new CompanyProfile("ACME CONSULTING LTD", "11044276", "active", "2017-10-18", null,
                        "25 Old Broad Street, London, EC2N 1HN"),
                List.of(
                        new Officer("WILLIAMS, Emma", "director", "2017-10-18", null),
                        new Officer("TAYLOR, Mark", "director", "2018-05-01", "2021-09-15")
                ),
                List.of(
                        new CompaniesHouseData.RawAccountFiling("2024-12-01", "2024-01-31", "AA"),
                        new CompaniesHouseData.RawAccountFiling("2023-10-31", "2023-01-31", "AA"),
                        new CompaniesHouseData.RawAccountFiling("2023-03-03", "2022-01-31", "AA")
                ),
                List.of(
                        new CompaniesHouseData.RawConfirmationStatementFiling("2024-06-07", "2024-06-07", "CS01"),
                        new CompaniesHouseData.RawConfirmationStatementFiling("2023-06-15", "2023-06-05", "CS01"),
                        new CompaniesHouseData.RawConfirmationStatementFiling("2022-06-05", "2022-06-05", "CS01")
                ),
                List.of()
        );
    }

    private static CompaniesHouseData buildGlobexCorp() {
        return new CompaniesHouseData(
                new CompanyProfile("GLOBEX CORPORATION LTD", "99887766", "dissolved", "2010-01-05", "2023-06-15",
                        "1 Canada Square, London, E14 5AB"),
                List.of(
                        new Officer("HANK, Scorpio", "director", "2010-01-05", "2023-06-15")
                ),
                List.of(
                        new CompaniesHouseData.RawAccountFiling("2022-09-01", "2022-01-05", "AA"),
                        new CompaniesHouseData.RawAccountFiling("2021-08-20", "2021-01-05", "AA")
                ),
                List.of(
                        new CompaniesHouseData.RawConfirmationStatementFiling("2022-01-20", "2022-01-05", "CS01"),
                        new CompaniesHouseData.RawConfirmationStatementFiling("2021-01-18", "2021-01-05", "CS01")
                ),
                List.of(
                        new CompaniesHouseData.RawLiquidationFiling("2023-03-01", "LRESSP", "Statement of affairs"),
                        new CompaniesHouseData.RawLiquidationFiling("2023-06-15", "LIQ02", "Final account in a members voluntary winding up")
                )
        );
    }

    private static CompaniesHouseData buildDefault(String companyNumber, String companyName) {
        return new CompaniesHouseData(
                new CompanyProfile(companyName != null ? companyName : "UNKNOWN COMPANY", companyNumber, "active", "2020-01-01", null,
                        "1 Mock Street, London, EC1A 1BB"),
                List.of(new Officer("DEFAULT, Director", "director", "2020-01-01", null)),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
