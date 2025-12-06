package dev.velocirawesome.etl.repository;

import dev.velocirawesome.etl.model.entity.Country;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.inline;

@Repository
public class CountryRepository {

    private final DSLContext dsl;

    // Type-safe field definitions for countries job tables
    private static final Field<String> CODE_FIELD = field("CODE", String.class);
    private static final Field<JSON> DATA_FIELD = field("DATA", JSON.class);

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
                    .columns(CODE_FIELD, DATA_FIELD)
                    .values(inline(country.getCode()), inline(JSON.valueOf(country.getData().toString())))
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
        // Use type-safe field references instead of string literals
        country.setCode(record.get(CODE_FIELD));

        JSON jsonData = record.get(DATA_FIELD);
        if (jsonData != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                // Convert jOOQ JSON to String first, then parse
                JsonNode parsedData = mapper.readTree(jsonData.data());
                country.setData(parsedData);
            } catch (Exception e) {
                // If parsing fails, set null
                country.setData(null);
            }
        }

        return country;
    }
}
