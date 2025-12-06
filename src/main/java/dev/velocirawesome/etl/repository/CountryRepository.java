package dev.velocirawesome.etl.repository;

import dev.velocirawesome.etl.model.entity.Country;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class CountryRepository {

    private final DSLContext dsl;

    public CountryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public void createJobTable(Long jobId) {
        String tableName = "countries_job_" + jobId;
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "code VARCHAR(3) PRIMARY KEY, " +
                "data JSON NOT NULL" +
                ")";
        dsl.execute(sql);
    }

    @Transactional
    public void insertCountries(Long jobId, List<Country> countries) {
        String tableName = "countries_job_" + jobId;

        for (Country country : countries) {
            dsl.insertInto(table(tableName))
                    .columns(field("code"), field("data"))
                    .values(country.getCode(), country.getData().toString())
                    .execute();
        }
    }

    public List<Country> getCountriesByJobId(Long jobId) {
        String tableName = "countries_job_" + jobId;

        var records = dsl.select()
                .from(table(tableName))
                .fetch();

        List<Country> countries = new ArrayList<>();
        for (var record : records) {
            Country country = mapRecordToCountry(record);
            countries.add(country);
        }

        return countries;
    }

    private Country mapRecordToCountry(org.jooq.Record record) {
        Country country = new Country();
        country.setCode(record.get("code", String.class));

        String dataStr = record.get("data", String.class);
        if (dataStr != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonData = mapper.readTree(dataStr);
                country.setData(jsonData);
            } catch (Exception e) {
                // If parsing fails, set null
                country.setData(null);
            }
        }

        return country;
    }
}
