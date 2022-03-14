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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.jsontree.JsonArray;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramStage;
import org.hisp.dhis.relationship.Relationship;
import org.hisp.dhis.relationship.RelationshipEntity;
import org.hisp.dhis.relationship.RelationshipItem;
import org.hisp.dhis.relationship.RelationshipType;
import org.hisp.dhis.security.acl.AccessStringHelper;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.sharing.UserAccess;
import org.hisp.dhis.webapi.DhisControllerConvenienceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

// TODO can I extract these helpers?

/**
 * {@link TrackerTrackedEntitiesExportControllerIntegrationTest} for more tests
 * of the
 * {@link org.hisp.dhis.webapi.controller.tracker.export.TrackerTrackedEntitiesExportController}.
 */
class TrackerTrackedEntitiesExportControllerTest extends DhisControllerConvenienceTest
{

    @Autowired
    private IdentifiableObjectManager manager;

    private OrganisationUnit orgUnit;

    private OrganisationUnit anotherOrgUnit;

    private Program program;

    private ProgramStage programStage;

    private TrackedEntityType trackedEntityType;

    private User owner;

    private User user;

    @BeforeEach
    void setUp()
    {
        owner = createUser( "owner" );

        orgUnit = createOrganisationUnit( 'A' );
        orgUnit.getSharing().setOwner( owner );
        manager.save( orgUnit, false );

        anotherOrgUnit = createOrganisationUnit( 'B' );
        anotherOrgUnit.getSharing().setOwner( owner );
        manager.save( anotherOrgUnit, false );

        user = createUserWithId( "tester", CodeGenerator.generateUid() );
        user.addOrganisationUnit( orgUnit );
        user.setTeiSearchOrganisationUnits( Set.of( orgUnit ) );
        this.userService.updateUser( user );

        program = createProgram( 'A' );
        program.addOrganisationUnit( orgUnit );
        program.getSharing().setOwner( owner );
        program.getSharing().addUserAccess( userAccess() );
        manager.save( program, false );

        programStage = createProgramStage( 'A', program );
        programStage.getSharing().setOwner( owner );
        programStage.getSharing().addUserAccess( userAccess() );
        manager.save( programStage, false );

        trackedEntityType = trackedEntityTypeAccessible();
    }

    @Test
    void getTrackedEntitiesNeedsProgramOrType()
    {
        assertEquals( "Either Program or Tracked entity type should be specified",
            GET( "/tracker/trackedEntities" )
                .error( HttpStatus.CONFLICT )
                .getMessage() );
    }

    @Test
    void getTrackedEntitiesNeedsProgramOrTrackedEntityType()
    {
        this.switchContextToUser( user );

        assertEquals( "Either Program or Tracked entity type should be specified",
            GET( "/tracker/trackedEntities?orgUnit={ou}", orgUnit.getUid() )
                .error( HttpStatus.CONFLICT )
                .getMessage() );
    }

    @Test
    void getTrackedEntitiesNeedsAtLeastOneOrgUnit()
    {
        assertEquals( "At least one organisation unit must be specified",
            GET( "/tracker/trackedEntities?program={program}", program.getUid() )
                .error( HttpStatus.CONFLICT )
                .getMessage() );
    }

    @Test
    void getTrackedEntityById()
    {
        TrackedEntityInstance tei = trackedEntityInstance();
        this.switchContextToUser( user );

        JsonObject json = GET( "/tracker/trackedEntities/{id}", tei.getUid() )
            .content( HttpStatus.OK );

        assertFalse( json.isEmpty() );
        assertEquals( tei.getUid(), json.getString( "trackedEntity" ).string() );
        assertTrue( json.getArray( "relationships" ).isEmpty(), "relationships are not returned by default" );
    }

    @Test
    void getTrackedEntityByIdDoesNotReturnRelationshipsByDefault()
    {
        TrackedEntityInstance from = trackedEntityInstance();
        TrackedEntityInstance to = trackedEntityInstance();
        relationship( from, to );
        this.switchContextToUser( user );

        JsonObject json = GET( "/tracker/trackedEntities/{id}", from.getUid() )
            .content( HttpStatus.OK );

        assertFalse( json.isEmpty() );
        assertEquals( from.getUid(), json.getString( "trackedEntity" ).string() );
        assertTrue( json.getArray( "relationships" ).isEmpty(), "relationships are not returned by default" );
    }

    @Test
    void getTrackedEntityByIdWithFieldsRelationships()
    {
        TrackedEntityInstance from = trackedEntityInstance();
        TrackedEntityInstance to = trackedEntityInstance();
        Relationship r = relationship( from, to );
        this.switchContextToUser( user );

        JsonObject json = GET( "/tracker/trackedEntities/{id}?fields=relationships", from.getUid() )
            .content( HttpStatus.OK );

        assertEquals( from.getUid(), json.getString( "trackedEntity" ).string(),
            "returned even though `fields` is only set to relationships DHIS2-12660" );
        JsonArray rels = json.getArray( "relationships" );
        assertFalse( rels.isEmpty(), "relationships are returned if `fields` contains relationships" );
        assertEquals( 1, rels.size() );
        assertRelationship( rels.getObject( 0 ), r );
        assertTrackedEntity( rels.getObject( 0 ).getObject( "from" ), from );
        assertTrackedEntity( rels.getObject( 0 ).getObject( "to" ), to );
    }

    @Test
    void getTrackedEntityByIdWithFieldsRelationshipsNoAccessToRelationshipType()
    {
        TrackedEntityInstance from = trackedEntityInstance();
        TrackedEntityInstance to = trackedEntityInstance();
        relationship( relationshipTypeNotAccessible(), from, to );
        this.switchContextToUser( user );

        JsonObject json = GET( "/tracker/trackedEntities/{id}?fields=relationships", from.getUid() )
            .content( HttpStatus.OK );

        assertFalse( json.isEmpty() );
        assertEquals( from.getUid(), json.getString( "trackedEntity" ).string(),
            "returned even though `fields` is only set to relationships DHIS2-12660" );
        assertTrue( json.getArray( "relationships" ).isEmpty(),
            "user needs access to relationship type to access the relationship" );
    }

    @Test
    void getTrackedEntityByIdWithFieldsRelationshipsNoAccessToRelationshipItemTo()
    {
        TrackedEntityInstance from = trackedEntityInstance();
        TrackedEntityInstance to = trackedEntityInstanceNotInSearchScope();
        relationship( from, to );
        this.switchContextToUser( user );

        JsonObject json = GET( "/tracker/trackedEntities/{id}?fields=relationships", from.getUid() )
            .content( HttpStatus.OK );

        assertFalse( json.isEmpty() );
        assertEquals( from.getUid(), json.getString( "trackedEntity" ).string(),
            "returned even though `fields` is only set to relationships DHIS2-12660" );
        assertTrue( json.getArray( "relationships" ).isEmpty(),
            "user needs access to from and to items to access the relationship" );
    }

    @Test
    void getTrackedEntityByIdWithFieldsRelationshipsNoAccessToBothRelationshipItems()
    {
        TrackedEntityInstance from = trackedEntityInstanceNotInSearchScope();
        TrackedEntityInstance to = trackedEntityInstanceNotInSearchScope();
        relationship( from, to );
        this.switchContextToUser( user );

        assertTrue( GET( "/tracker/trackedEntities/{id}?fields=relationships", from.getUid() )
            .error( HttpStatus.CONFLICT ).getMessage()
            .contains( "User has no read access to organisation unit" ) );
    }

    @Test
    void getTrackedEntityByIdWithFieldsRelationshipsNoAccessToRelationshipItemFrom()
    {
        TrackedEntityInstance from = trackedEntityInstanceNotInSearchScope();
        TrackedEntityInstance to = trackedEntityInstance();
        relationship( from, to );
        this.switchContextToUser( user );

        assertTrue( GET( "/tracker/trackedEntities/{id}?fields=relationships", from.getUid() )
            .error( HttpStatus.CONFLICT ).getMessage()
            .contains( "User has no read access to organisation unit" ) );
    }

    @Test
    void getTrackedEntityByIdyWithFieldsRelationshipsNoAccessToTrackedEntityType()
    {
        TrackedEntityType type = trackedEntityTypeNotAccessible();
        TrackedEntityInstance from = trackedEntityInstance( type );
        TrackedEntityInstance to = trackedEntityInstance( type );
        relationship( from, to );
        this.switchContextToUser( user );

        assertTrue( GET( "/tracker/trackedEntities/{id}?fields=relationships", from.getUid() )
            .error( HttpStatus.CONFLICT ).getMessage()
            .contains( "User has no data read access to tracked entity" ) );
    }

    @Test
    void getTrackedEntityByIdNotFound()
    {
        assertEquals( "TrackedEntityInstance not found for uid: Hq3Kc6HK4OZ",
            GET( "/tracker/trackedEntities/Hq3Kc6HK4OZ" )
                .error( HttpStatus.NOT_FOUND )
                .getMessage() );
    }

    private TrackedEntityType trackedEntityTypeAccessible()
    {
        TrackedEntityType type = trackedEntityType( 'A' );
        type.getSharing().addUserAccess( userAccess() );
        manager.save( type, false );
        return type;
    }

    private TrackedEntityType trackedEntityTypeNotAccessible()
    {
        TrackedEntityType type = trackedEntityType( 'B' );
        manager.save( type, false );
        return type;
    }

    private TrackedEntityType trackedEntityType( char uniqueChar )
    {
        TrackedEntityType type = createTrackedEntityType( uniqueChar );
        type.getSharing().setOwner( owner );
        type.getSharing().setPublicAccess( AccessStringHelper.DEFAULT );
        return type;
    }

    private TrackedEntityInstance trackedEntityInstance()
    {
        TrackedEntityInstance tei = trackedEntityInstance( orgUnit );
        manager.save( tei, false );
        return tei;
    }

    private TrackedEntityInstance trackedEntityInstanceNotInSearchScope()
    {
        TrackedEntityInstance tei = trackedEntityInstance( anotherOrgUnit );
        manager.save( tei, false );
        return tei;
    }

    private TrackedEntityInstance trackedEntityInstance( TrackedEntityType trackedEntityType )
    {
        TrackedEntityInstance tei = trackedEntityInstance( orgUnit, trackedEntityType );
        manager.save( tei, false );
        return tei;
    }

    private TrackedEntityInstance trackedEntityInstance( OrganisationUnit orgUnit )
    {
        return trackedEntityInstance( orgUnit, trackedEntityType );
    }

    private TrackedEntityInstance trackedEntityInstance( OrganisationUnit orgUnit, TrackedEntityType trackedEntityType )
    {
        TrackedEntityInstance tei = createTrackedEntityInstance( orgUnit );
        tei.setTrackedEntityType( trackedEntityType );
        tei.getSharing().setPublicAccess( AccessStringHelper.DEFAULT );
        tei.getSharing().setOwner( owner );
        return tei;
    }

    private UserAccess userAccess()
    {
        UserAccess a = new UserAccess();
        a.setUser( user );
        a.setAccess( AccessStringHelper.FULL );
        return a;
    }

    private RelationshipType relationshipTypeAccessible( RelationshipEntity from,
        RelationshipEntity to )
    {
        RelationshipType type = relationshipType( from, to );
        type.getSharing().addUserAccess( userAccess() );
        manager.save( type, false );
        return type;
    }

    private RelationshipType relationshipTypeNotAccessible()
    {
        return relationshipType( RelationshipEntity.TRACKED_ENTITY_INSTANCE,
            RelationshipEntity.TRACKED_ENTITY_INSTANCE );
    }

    private RelationshipType relationshipType( RelationshipEntity from, RelationshipEntity to )
    {
        RelationshipType type = createRelationshipType( 'A' );
        type.getFromConstraint().setRelationshipEntity( from );
        type.getToConstraint().setRelationshipEntity( to );
        type.getSharing().setOwner( owner );
        type.getSharing().setPublicAccess( AccessStringHelper.DEFAULT );
        manager.save( type, false );
        return type;
    }

    private Relationship relationship( TrackedEntityInstance from, TrackedEntityInstance to )
    {
        RelationshipType type = relationshipTypeAccessible( RelationshipEntity.TRACKED_ENTITY_INSTANCE,
            RelationshipEntity.TRACKED_ENTITY_INSTANCE );
        return relationship( type, from, to );
    }

    private Relationship relationship( RelationshipType type, TrackedEntityInstance from, TrackedEntityInstance to )
    {
        Relationship r = new Relationship();

        RelationshipItem fromItem = new RelationshipItem();
        fromItem.setTrackedEntityInstance( from );
        from.getRelationshipItems().add( fromItem );
        r.setFrom( fromItem );
        fromItem.setRelationship( r );

        RelationshipItem toItem = new RelationshipItem();
        toItem.setTrackedEntityInstance( to );
        to.getRelationshipItems().add( toItem );
        r.setTo( toItem );
        toItem.setRelationship( r );

        r.setRelationshipType( type );
        r.setKey( type.getUid() );
        r.setInvertedKey( type.getUid() );
        r.setAutoFields();
        r.getSharing().setOwner( owner );
        manager.save( r, false );
        return r;
    }

    private void assertRelationship( JsonObject json, Relationship r )
    {
        assertFalse( json.isEmpty() );
        assertEquals( r.getUid(), json.getString( "relationship" ).string() );
        assertEquals( r.getRelationshipType().getUid(), json.getString( "relationshipType" ).string() );
    }

    private void assertTrackedEntity( JsonObject json, TrackedEntityInstance tei )
    {
        JsonObject jsonTEI = json.getObject( "trackedEntity" );
        assertEquals( tei.getUid(), jsonTEI.getString( "trackedEntity" ).string() );
        assertFalse( jsonTEI.has( "trackedEntityType" ) );
        assertFalse( jsonTEI.has( "orgUnit" ) );
        assertFalse( jsonTEI.has( "relationships" ), "relationships is not returned within relationship items" );
        assertTrue( jsonTEI.getArray( "attributes" ).isEmpty() );
    }
}