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
package org.hisp.dhis.tracker.validation.hooks;

import static com.google.api.client.util.Preconditions.checkNotNull;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1056;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1057;

import java.time.Instant;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.ObjectUtils;
import org.hisp.dhis.category.CategoryOption;
import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.i18n.I18nFormat;
import org.hisp.dhis.i18n.I18nManager;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.report.TrackerErrorCode;
import org.hisp.dhis.tracker.report.ValidationErrorReporter;
import org.hisp.dhis.tracker.validation.TrackerImportValidationContext;
import org.hisp.dhis.util.DateUtils;
import org.springframework.stereotype.Component;

/**
 * @author Morten Svanæs <msvanaes@dhis2.org>
 */
@Component
@Slf4j
public class EventCategoryOptValidationHook
    extends AbstractTrackerDtoValidationHook
{
    private final I18nManager i18nManager;

    public EventCategoryOptValidationHook( I18nManager i18nManager )
    {
        checkNotNull( i18nManager );
        this.i18nManager = i18nManager;
    }

    @Override
    public void validateEvent( ValidationErrorReporter reporter, Event event )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();

        Program program = context.getProgram( event.getProgram() );
        checkNotNull( program, TrackerImporterAssertErrors.PROGRAM_CANT_BE_NULL );
        checkNotNull( context.getBundle().getUser(), TrackerImporterAssertErrors.USER_CANT_BE_NULL );
        checkNotNull( program, TrackerImporterAssertErrors.PROGRAM_CANT_BE_NULL );
        checkNotNull( event, TrackerImporterAssertErrors.EVENT_CANT_BE_NULL );

        // DHIS2-12460
        // event
        // "program": "kla3mAPgvCH",
        // "attributeCategoryOptions": "CW81uF03hvV",
        // is a valid category option of the Category "Implementing Partner"
        // which is a valid category of the Category Combination "Implementing
        // Partner"
        // which is the Category combination of the referenced program
        // It seems to me that we have all the required information to determine
        // that the given
        // attributeCategoryOptions value is valid

        // Why are we fetching the categoryOptionCombo out of some cache using
        // the event uid. What if its a new
        // event. It could only be the default then. No?

        // Should the cache or the preheat not contain something where we can
        // say .get(attributeCategoryOptions) and we
        // get something back that allows us to compare it to the program
        // reference? If they are not equal it means the
        // event contains an option that is invalid.

        CategoryOptionCombo categoryOptionCombo = reporter.getValidationContext()
            .getCachedEventCategoryOptionCombo( event.getUid() );

        checkNotNull( categoryOptionCombo, TrackerImporterAssertErrors.CATEGORY_OPTION_COMBO_CANT_BE_NULL );

        // TODO(DHIS2-12460) it fails here
        // how could I fix it with the information we have?
        if ( categoryOptionCombo.isDefault()
            && program.getCategoryCombo() != null
            && !program.getCategoryCombo().isDefault() )
        {
            reporter.addError( event, TrackerErrorCode.E1055 );
            return;
        }

        Date eventDate;
        try
        {
            eventDate = DateUtils.fromInstant( ObjectUtils
                .firstNonNull( event.getOccurredAt(), event.getScheduledAt(), Instant.now() ) );
        }
        catch ( IllegalArgumentException e )
        {
            log.debug( "Failed to parse dates, an error should already be reported." );
            return;
        }

        I18nFormat i18nFormat = i18nManager.getI18nFormat();

        for ( CategoryOption option : categoryOptionCombo.getCategoryOptions() )
        {
            if ( option.getStartDate() != null && eventDate.compareTo( option.getStartDate() ) < 0 )
            {
                reporter.addError( event, E1056, i18nFormat.formatDate( eventDate ),
                    i18nFormat.formatDate( option.getStartDate() ), option.getName() );
            }

            if ( option.getEndDate() != null && eventDate.compareTo( option.getAdjustedEndDate( program ) ) > 0 )
            {
                reporter.addError( event, E1057, i18nFormat.formatDate( eventDate ),
                    i18nFormat.formatDate( option.getAdjustedEndDate( program ) ), option.getName(),
                    program.getName() );
            }
        }
    }
}
