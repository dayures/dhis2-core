/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.analytics.table;

import static org.hisp.dhis.analytics.util.DisplayNameUtils.getDisplayName;
import static org.hisp.dhis.commons.util.TextUtils.replace;
import static org.hisp.dhis.db.model.DataType.CHARACTER_11;
import static org.hisp.dhis.db.model.DataType.DOUBLE;
import static org.hisp.dhis.db.model.DataType.GEOMETRY;
import static org.hisp.dhis.db.model.DataType.INTEGER;
import static org.hisp.dhis.db.model.DataType.TEXT;
import static org.hisp.dhis.db.model.DataType.TIMESTAMP;
import static org.hisp.dhis.db.model.DataType.VARCHAR_255;
import static org.hisp.dhis.db.model.DataType.VARCHAR_50;
import static org.hisp.dhis.db.model.constraint.Nullable.NOT_NULL;
import static org.hisp.dhis.util.DateUtils.toLongDate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.hisp.dhis.analytics.AnalyticsTableHookService;
import org.hisp.dhis.analytics.AnalyticsTableType;
import org.hisp.dhis.analytics.AnalyticsTableUpdateParams;
import org.hisp.dhis.analytics.partition.PartitionManager;
import org.hisp.dhis.analytics.table.model.AnalyticsTable;
import org.hisp.dhis.analytics.table.model.AnalyticsTableColumn;
import org.hisp.dhis.analytics.table.model.AnalyticsTablePartition;
import org.hisp.dhis.analytics.table.setting.AnalyticsTableSettings;
import org.hisp.dhis.category.CategoryService;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.commons.collection.UniqueArrayList;
import org.hisp.dhis.dataapproval.DataApprovalLevelService;
import org.hisp.dhis.db.model.IndexType;
import org.hisp.dhis.db.model.Logged;
import org.hisp.dhis.db.sql.SqlBuilder;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.period.PeriodDataProvider;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.resourcetable.ResourceTableService;
import org.hisp.dhis.setting.SystemSettingManager;
import org.hisp.dhis.system.database.DatabaseInfoProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Markus Bekken
 */
@Service("org.hisp.dhis.analytics.EnrollmentAnalyticsTableManager")
public class JdbcEnrollmentAnalyticsTableManager extends AbstractEventJdbcTableManager {
  private static final List<AnalyticsTableColumn> FIXED_COLS =
      List.of(
          new AnalyticsTableColumn("pi", CHARACTER_11, NOT_NULL, "pi.uid"),
          new AnalyticsTableColumn("enrollmentdate", TIMESTAMP, "pi.enrollmentdate"),
          new AnalyticsTableColumn("incidentdate", TIMESTAMP, "pi.occurreddate"),
          new AnalyticsTableColumn(
              "completeddate",
              TIMESTAMP,
              "case pi.status when 'COMPLETED' then pi.completeddate end"),
          new AnalyticsTableColumn("lastupdated", TIMESTAMP, "pi.lastupdated"),
          new AnalyticsTableColumn("storedby", VARCHAR_255, "pi.storedby"),
          new AnalyticsTableColumn(
              "createdbyusername",
              VARCHAR_255,
              "pi.createdbyuserinfo ->> 'username' as createdbyusername"),
          new AnalyticsTableColumn(
              "createdbyname",
              VARCHAR_255,
              "pi.createdbyuserinfo ->> 'firstName' as createdbyname"),
          new AnalyticsTableColumn(
              "createdbylastname",
              VARCHAR_255,
              "pi.createdbyuserinfo ->> 'surname' as createdbylastname"),
          new AnalyticsTableColumn(
              "createdbydisplayname",
              VARCHAR_255,
              getDisplayName("createdbyuserinfo", "pi", "createdbydisplayname")),
          new AnalyticsTableColumn(
              "lastupdatedbyusername",
              VARCHAR_255,
              "pi.lastupdatedbyuserinfo ->> 'username' as lastupdatedbyusername"),
          new AnalyticsTableColumn(
              "lastupdatedbyname",
              VARCHAR_255,
              "pi.lastupdatedbyuserinfo ->> 'firstName' as lastupdatedbyname"),
          new AnalyticsTableColumn(
              "lastupdatedbylastname",
              VARCHAR_255,
              "pi.lastupdatedbyuserinfo ->> 'surname' as lastupdatedbylastname"),
          new AnalyticsTableColumn(
              "lastupdatedbydisplayname",
              VARCHAR_255,
              getDisplayName("lastupdatedbyuserinfo", "pi", "lastupdatedbydisplayname")),
          new AnalyticsTableColumn("enrollmentstatus", VARCHAR_50, "pi.status"),
          new AnalyticsTableColumn(
              "longitude",
              DOUBLE,
              "CASE WHEN 'POINT' = GeometryType(pi.geometry) THEN ST_X(pi.geometry) ELSE null END"),
          new AnalyticsTableColumn(
              "latitude",
              DOUBLE,
              "CASE WHEN 'POINT' = GeometryType(pi.geometry) THEN ST_Y(pi.geometry) ELSE null END"),
          new AnalyticsTableColumn("ou", CHARACTER_11, NOT_NULL, "ou.uid"),
          new AnalyticsTableColumn("ouname", TEXT, NOT_NULL, "ou.name"),
          new AnalyticsTableColumn("oucode", TEXT, "ou.code"),
          new AnalyticsTableColumn("oulevel", INTEGER, "ous.level"),
          new AnalyticsTableColumn("pigeometry", GEOMETRY, "pi.geometry", IndexType.GIST),
          new AnalyticsTableColumn(
              "registrationou", CHARACTER_11, NOT_NULL, "coalesce(registrationou.uid,ou.uid)"));

  public JdbcEnrollmentAnalyticsTableManager(
      IdentifiableObjectManager idObjectManager,
      OrganisationUnitService organisationUnitService,
      CategoryService categoryService,
      SystemSettingManager systemSettingManager,
      DataApprovalLevelService dataApprovalLevelService,
      ResourceTableService resourceTableService,
      AnalyticsTableHookService tableHookService,
      PartitionManager partitionManager,
      DatabaseInfoProvider databaseInfoProvider,
      @Qualifier("analyticsJdbcTemplate") JdbcTemplate jdbcTemplate,
      AnalyticsTableSettings analyticsExportSettings,
      PeriodDataProvider periodDataProvider,
      SqlBuilder sqlBuilder) {
    super(
        idObjectManager,
        organisationUnitService,
        categoryService,
        systemSettingManager,
        dataApprovalLevelService,
        resourceTableService,
        tableHookService,
        partitionManager,
        databaseInfoProvider,
        jdbcTemplate,
        analyticsExportSettings,
        periodDataProvider,
        sqlBuilder);
  }

  @Override
  public AnalyticsTableType getAnalyticsTableType() {
    return AnalyticsTableType.ENROLLMENT;
  }

  @Override
  @Transactional
  public List<AnalyticsTable> getAnalyticsTables(AnalyticsTableUpdateParams params) {
    return params.isLatestUpdate() ? List.of() : getRegularAnalyticsTables(params);
  }

  /**
   * Creates a list of {@link AnalyticsTable} for each program.
   *
   * @param params the {@link AnalyticsTableUpdateParams}.
   * @return a list of {@link AnalyticsTableUpdateParams}.
   */
  private List<AnalyticsTable> getRegularAnalyticsTables(AnalyticsTableUpdateParams params) {
    List<AnalyticsTable> tables = new UniqueArrayList<>();

    Logged logged = analyticsTableSettings.getTableLogged();
    List<Program> programs = idObjectManager.getAllNoAcl(Program.class);

    for (Program program : programs) {
      AnalyticsTable table =
          new AnalyticsTable(getAnalyticsTableType(), getColumns(program), logged, program);

      tables.add(table);
    }

    return tables;
  }

  @Override
  protected List<String> getPartitionChecks(Integer year, Date endDate) {
    return List.of();
  }

  @Override
  protected void populateTable(
      AnalyticsTableUpdateParams params, AnalyticsTablePartition partition) {
    Program program = partition.getMasterTable().getProgram();

    String fromClause =
        replace(
            """
            \s from enrollment pi \
            inner join program pr on pi.programid=pr.programid \
            left join trackedentity tei on pi.trackedentityid=tei.trackedentityid \
            and tei.deleted is false \
            left join organisationunit registrationou on tei.organisationunitid=registrationou.organisationunitid \
            inner join organisationunit ou on pi.organisationunitid=ou.organisationunitid \
            left join analytics_rs_orgunitstructure ous on pi.organisationunitid=ous.organisationunitid \
            left join analytics_rs_organisationunitgroupsetstructure ougs on pi.organisationunitid=ougs.organisationunitid \
            and (cast(date_trunc('month', pi.enrollmentdate) as date)=ougs.startdate or ougs.startdate is null) \
            left join analytics_rs_dateperiodstructure dps on cast(pi.enrollmentdate as date)=dps.dateperiod \
            where pr.programid=${programId}  \
            and pi.organisationunitid is not null \
            and pi.lastupdated <= '${startTime}' \
            and pi.occurreddate is not null \
            and pi.deleted is false\s""",
            Map.of(
                "programId", String.valueOf(program.getId()),
                "startTime", toLongDate(params.getStartTime())));

    populateTableInternal(partition, fromClause);
  }

  private List<AnalyticsTableColumn> getColumns(Program program) {
    List<AnalyticsTableColumn> columns = new ArrayList<>();

    columns.addAll(getOrganisationUnitLevelColumns());
    columns.add(getOrganisationUnitNameHierarchyColumn());
    columns.addAll(getOrganisationUnitGroupSetColumns());
    columns.addAll(getPeriodTypeColumns("dps"));
    columns.addAll(getTrackedEntityAttributeColumns(program));
    columns.addAll(FIXED_COLS);

    if (program.isRegistration()) {
      columns.add(new AnalyticsTableColumn("tei", CHARACTER_11, "tei.uid"));
      columns.add(new AnalyticsTableColumn("teigeometry", GEOMETRY, "tei.geometry"));
    }

    return filterDimensionColumns(columns);
  }
}
