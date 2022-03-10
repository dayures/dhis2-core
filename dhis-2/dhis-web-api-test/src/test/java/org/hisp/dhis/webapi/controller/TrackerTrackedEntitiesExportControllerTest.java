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
package org.hisp.dhis.webapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import java.util.stream.Collectors;

import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramStage;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.webapi.DhisControllerConvenienceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class TrackerTrackedEntitiesExportControllerTest extends DhisControllerConvenienceTest
{

    @Autowired
    private IdentifiableObjectManager manager;

    private OrganisationUnit orgUnit;

    private Program program;

    private ProgramStage programStage;

    private TrackedEntityType trackedEntityType;

    @BeforeEach
    void setUp()
    {
        // orgUnit = this.getCurrentUser().getOrganisationUnit();
        orgUnit = createOrganisationUnit( 'A' );
        manager.save( orgUnit );
        program = createProgram( 'A' );
        program.setOrganisationUnits( Set.of( orgUnit ) );
        manager.save( program );
        programStage = createProgramStage( 'A', program );
        manager.save( programStage );
        trackedEntityType = createTrackedEntityType( 'A' );
        manager.save( trackedEntityType );
    }

    @Test
    void getTrackedEntityInstances()
    {
        TrackedEntityInstance tei1 = trackedEntityInstance();
        TrackedEntityInstance tei2 = trackedEntityInstance();

        JsonObject json = GET( "/tracker/trackedEntities?program={program}&orgUnit={orgUnit}", program.getUid(),
            orgUnit.getUid() )
                .content( HttpStatus.OK );
        assertFalse( json.isEmpty() );
        JsonList<JsonObject> teis = json.getList( "instances", JsonObject.class );
        assertEquals( 2, teis.size() );
        System.out.println( teis.stream().map( e -> e.getString( "trackedEntity" ) ).collect( Collectors.toList() ) );
    }

    @Test
    void getTrackedEntityInstancesNeedsAtLeastOneOrgUnit()
    {
        assertEquals( "At least one organisation unit must be specified",
            GET( "/tracker/trackedEntities?program={program}", program.getUid() )
                .error( HttpStatus.CONFLICT )
                .getMessage() );
    }

    @Test
    void getTrackedEntityInstancesNeedsProgramOrType()
    {
        assertEquals( "Either Program or Tracked entity type should be specified",
            GET( "/tracker/trackedEntities" )
                .error( HttpStatus.CONFLICT )
                .getMessage() );
    }

    @Test
    void getTrackedEntityInstanceById()
    {
        TrackedEntityInstance tei = trackedEntityInstance();

        JsonObject json = GET( "/tracker/trackedEntities/{id}", tei.getUid() )
            .content( HttpStatus.OK );
        assertFalse( json.isEmpty() );
        assertEquals( tei.getUid(), json.getString( "trackedEntity" ).string() );
    }

    @Test
    void getTrackedEntityByIdNotFound()
    {
        assertEquals( "TrackedEntityInstance not found for uid: Hq3Kc6HK4OZ",
            GET( "/tracker/trackedEntities/Hq3Kc6HK4OZ" )
                .error( HttpStatus.NOT_FOUND )
                .getMessage() );
    }

    private TrackedEntityInstance trackedEntityInstance()
    {
        TrackedEntityInstance tei = createTrackedEntityInstance( orgUnit );
        tei.setTrackedEntityType( trackedEntityType );
        manager.save( tei );
        return tei;
    }
}