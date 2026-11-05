package ch.sbb.polarion.extension.pdf_exporter.job;

import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.pdf_exporter.converter.PdfConverter;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.CommentsRenderType;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.ImageDensity;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.LinkRoleDirection;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.Orientation;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.PaperSize;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.PdfVariant;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.settings.stylepackage.StylePackageModel;
import ch.sbb.polarion.extension.pdf_exporter.service.PdfExporterPolarionService;
import ch.sbb.polarion.extension.pdf_exporter.settings.StylePackageSettings;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IFolder;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.IModuleManager;
import com.polarion.alm.tracker.internal.workflow.Arguments;
import com.polarion.alm.tracker.model.IAttachment;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.workflow.ICallContext;
import com.polarion.core.util.exceptions.UserFriendlyRuntimeException;
import com.polarion.core.util.types.Text;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.persistence.IDataService;
import com.polarion.subterra.base.SubterraURI;
import com.polarion.subterra.base.location.ILocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, CurrentContextExtension.class})
class GeneratePdfJobUnitImplTest {

    private static final String OBJECT_URI = "subterra:data-service:objects:/default/project${WorkItem}WI-1";

    PdfExporterPolarionService pdfExporterPolarionService;
    PdfConverter pdfConverter;
    ITrackerService trackerService;
    IDataService dataService;
    GeneratePdfJobUnitImpl jobUnit;
    IModule module;
    IFolder moduleFolder;
    ITrackerProject trackerProject;
    @Mock
    private SettingsService settingsService;

    @BeforeEach
    void beforeEach() {
        NamedSettingsRegistry.INSTANCE.getAll().clear();
        pdfExporterPolarionService = mock(PdfExporterPolarionService.class);
        pdfConverter = mock(PdfConverter.class);
        trackerService = mock(ITrackerService.class);
        dataService = mock(IDataService.class);
        lenient().when(trackerService.getDataService()).thenReturn(dataService);
        jobUnit = spy(new GeneratePdfJobUnitImpl(GeneratePdfJobUnit.JOB_NAME, mock(IJobUnitFactory.class), pdfExporterPolarionService, pdfConverter, trackerService, 0));
        jobUnit.setJob(mock(IJob.class, Answers.RETURNS_DEEP_STUBS));
        trackerProject = mock(ITrackerProject.class);
        lenient().when(pdfExporterPolarionService.getTrackerProject(anyString())).thenReturn(trackerProject);
        lenient().when(pdfExporterPolarionService.userAuthorizedForExport(nullable(String.class))).thenReturn(true);
        module = mock(IModule.class);
        ILocation moduleLocation = mock(ILocation.class);
        lenient().when(module.getModuleLocation()).thenReturn(moduleLocation);
        moduleFolder = mock(IFolder.class);
        lenient().when(module.getFolder()).thenReturn(moduleFolder);
        IStatusOpt moduleStatus = mock(IStatusOpt.class);
        lenient().when(moduleStatus.getId()).thenReturn("currentStatusId");
        lenient().when(module.getStatus()).thenReturn(moduleStatus);
    }

    @AfterEach
    void afterEach() {
        NamedSettingsRegistry.INSTANCE.getAll().clear();
    }

    private IJobStatus run(Object target, Map<String, String> args) {
        jobUnit.setObjectUri(OBJECT_URI);
        jobUnit.setArguments(new Arguments(args));
        jobUnit.setContext(mock(ICallContext.class));
        try (MockedStatic<SubterraURI> uri = mockStatic(SubterraURI.class)) {
            SubterraURI subterraURI = mock(SubterraURI.class);
            uri.when(() -> SubterraURI.fromString(OBJECT_URI)).thenReturn(subterraURI);
            when(dataService.getInstance(subterraURI)).thenReturn(target);
            return jobUnit.runInternal(mock(IProgressMonitor.class));
        }
    }

    @Test
    void testRunFailsWhenUserNotAuthorized() {
        when(module.getProjectId()).thenReturn("testProject");
        when(pdfExporterPolarionService.userAuthorizedForExport("testProject")).thenReturn(false);

        IJobStatus status = run(module, Map.of());

        assertEquals(IJobStatus.JobStatusType.STATUS_TYPE_FAILED, status.getType());
        assertInstanceOf(SecurityException.class, status.getError());
        verify(pdfConverter, never()).convertToPdf(any(), any());
    }

    @Test
    void testRunFailsWhenDocumentOfWorkItemNotFound() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn("WI-1");
        when(workItem.getProjectId()).thenReturn("project");
        IProjectService projectService = mock(IProjectService.class);
        when(trackerService.getProjectsService()).thenReturn(projectService);
        when(projectService.getProject("project")).thenReturn(mock(IProject.class));
        when(trackerService.getModuleManager()).thenReturn(mock(IModuleManager.class));

        IJobStatus status = run(workItem, Map.of("existing_wi_id", "Space"));

        assertEquals(IJobStatus.JobStatusType.STATUS_TYPE_FAILED, status.getType());
        assertInstanceOf(UserFriendlyRuntimeException.class, status.getError());
        assertEquals("Document 'Space/WI-1' in project 'project' not found.", status.getError().getMessage());
        verify(pdfConverter, never()).convertToPdf(any(), any());
    }

    @Test
    void testRunFailsWhenSpaceOfWorkItemNotConfigured() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn("WI-1");

        IJobStatus status = run(workItem, Map.of());

        assertEquals(IJobStatus.JobStatusType.STATUS_TYPE_FAILED, status.getType());
        assertInstanceOf(UserFriendlyRuntimeException.class, status.getError());
        verify(pdfConverter, never()).convertToPdf(any(), any());
    }

    @Test
    void testRunFailsWhenReferencedFieldIsEmpty() {
        IJobStatus status = run(module, Map.of("existing_wi_id", "{wi:workItemReference}"));

        assertEquals(IJobStatus.JobStatusType.STATUS_TYPE_FAILED, status.getType());
        assertEquals("The field 'workItemReference' does not exist or is empty.", status.getError().getMessage());
        verify(pdfConverter, never()).convertToPdf(any(), any());
    }

    @Test
    void testResolveModuleOfWorkItem() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn("WI-1");
        when(workItem.getProjectId()).thenReturn("project");
        IProjectService projectService = mock(IProjectService.class);
        IProject project = mock(IProject.class);
        when(trackerService.getProjectsService()).thenReturn(projectService);
        when(projectService.getProject("project")).thenReturn(project);
        IModuleManager moduleManager = mock(IModuleManager.class);
        when(trackerService.getModuleManager()).thenReturn(moduleManager);
        when(moduleManager.getModule(eq(project), argThat(location -> "Space/WI-1".equals(location.getLocationPath())))).thenReturn(module);

        assertSame(module, jobUnit.resolveModule(workItem, "Space"));
        assertSame(module, jobUnit.resolveModule(module, null));
    }

    @Test
    void testResolveWorkItemId() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getId()).thenReturn("WI-1");
        when(module.getValue("ref")).thenReturn("WI-2");

        assertEquals("WI-1", jobUnit.resolveWorkItemId(workItem, null));
        assertEquals("WI-2", jobUnit.resolveWorkItemId(module, "{doc:ref}"));
        assertEquals("WI-3", jobUnit.resolveWorkItemId(module, "WI-3"));
        assertNull(jobUnit.resolveWorkItemId(module, null));
    }

    @Test
    void testDefaultStylePackageUsed() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new StylePackageSettings(settingsService)));

        assertEquals("Default", jobUnit.getExportParams(module, new Arguments(Map.of())).getCss());
    }

    @Test
    void testWorkItemsQueryFromStylePackageIsPropagated() {
        StylePackageSettings stylePackageSettings = spy(new StylePackageSettings(settingsService));
        StylePackageModel withQuery = stylePackageSettings.defaultValues();
        withQuery.setWorkItemsQuery("type:requirement");
        doReturn(withQuery).when(stylePackageSettings).read(any(), any(), any());
        NamedSettingsRegistry.INSTANCE.register(List.of(stylePackageSettings));

        ExportParams exportParams = jobUnit.getExportParams(module, new Arguments(Map.of()));
        assertNotNull(exportParams.getUrlQueryParameters());
        assertEquals("type:requirement", exportParams.getUrlQueryParameters().get(ExportParams.URL_QUERY_PARAM_QUERY));
    }

    @Test
    void testCommentsLinksAttachmentsAndFontsFromStylePackageArePropagated() {
        StylePackageSettings stylePackageSettings = spy(new StylePackageSettings(settingsService));
        StylePackageModel stylePackage = stylePackageSettings.defaultValues();
        stylePackage.setFirstPageHeaderFooter("Title page");
        stylePackage.setRenderNativeComments(true);
        stylePackage.setIncludeUnreferencedComments(true);
        stylePackage.setLinkRoleDirection("reverse");
        stylePackage.setEmbedAttachments(true);
        stylePackage.setFullFonts(true);
        doReturn(stylePackage).when(stylePackageSettings).read(any(), any(), any());
        NamedSettingsRegistry.INSTANCE.register(List.of(stylePackageSettings));

        ExportParams exportParams = jobUnit.getExportParams(module, new Arguments(Map.of()));
        assertEquals("Title page", exportParams.getFirstPageHeaderFooter());
        assertTrue(exportParams.isRenderNativeComments());
        assertTrue(exportParams.isIncludeUnreferencedComments());
        assertEquals(LinkRoleDirection.REVERSE, exportParams.getLinkRoleDirection());
        assertTrue(exportParams.isEmbedAttachments());
        assertTrue(exportParams.isFullFonts());
    }

    @Test
    @SuppressWarnings("java:S5961") // Suppress "Refactor this method to reduce the number of assertions" warning
    void testOtherSettingsFromStylePackageArePropagated() {
        when(module.getProjectId()).thenReturn("testProject");
        when(module.getModuleLocation().getLocationPath()).thenReturn("Space/Document");
        StylePackageSettings stylePackageSettings = spy(new StylePackageSettings(settingsService));
        StylePackageModel stylePackage = stylePackageSettings.defaultValues();
        stylePackage.setCoverPage("coverPage1");
        stylePackage.setHeaderFooter("headerFooter1");
        stylePackage.setLocalization("localization1");
        stylePackage.setWebhooks("webhooks1");
        stylePackage.setHeadersColor("#123456");
        stylePackage.setOrientation("landscape");
        stylePackage.setPaperSize("a3");
        stylePackage.setPdfVariant("pdf_ua_1");
        stylePackage.setImageDensity("dpi_300");
        stylePackage.setFitToPage(false);
        stylePackage.setRenderComments(CommentsRenderType.ALL);
        stylePackage.setWatermark(true);
        stylePackage.setMarkReferencedWorkitems(true);
        stylePackage.setCutEmptyChapters(true);
        stylePackage.setCutEmptyWorkitemAttributes(false);
        stylePackage.setCutLocalURLs(true);
        stylePackage.setFollowHTMLPresentationalHints(false);
        stylePackage.setCustomNumberedListStyles("numberedListStyles1");
        stylePackage.setSpecificChapters("1,2");
        stylePackage.setMetadataFields("field1,field2");
        stylePackage.setLanguage("de");
        stylePackage.setLanguageCustomField("languageField");
        stylePackage.setLinkedWorkitemRoles(List.of("role1", "role2"));
        stylePackage.setAttachmentsFilter("*.pdf");
        stylePackage.setTestcaseFieldId("testcaseField");
        doReturn(stylePackage).when(stylePackageSettings).read(any(), any(), any());
        NamedSettingsRegistry.INSTANCE.register(List.of(stylePackageSettings));

        ExportParams exportParams = jobUnit.getExportParams(module, new Arguments(Map.of()));
        assertEquals("testProject", exportParams.getProjectId());
        assertEquals("Space/Document", exportParams.getLocationPath());
        assertEquals("coverPage1", exportParams.getCoverPage());
        assertEquals("headerFooter1", exportParams.getHeaderFooter());
        assertEquals("localization1", exportParams.getLocalization());
        assertEquals("webhooks1", exportParams.getWebhooks());
        assertEquals("#123456", exportParams.getHeadersColor());
        assertEquals(Orientation.LANDSCAPE, exportParams.getOrientation());
        assertEquals(PaperSize.A3, exportParams.getPaperSize());
        assertEquals(PdfVariant.PDF_UA_1, exportParams.getPdfVariant());
        assertEquals(ImageDensity.DPI_300, exportParams.getImageDensity());
        assertFalse(exportParams.isFitToPage());
        assertEquals(CommentsRenderType.ALL, exportParams.getRenderComments());
        assertTrue(exportParams.isWatermark());
        assertTrue(exportParams.isMarkReferencedWorkitems());
        assertTrue(exportParams.isCutEmptyChapters());
        assertFalse(exportParams.isCutEmptyWIAttributes());
        assertTrue(exportParams.isCutLocalUrls());
        assertFalse(exportParams.isFollowHTMLPresentationalHints());
        assertEquals("numberedListStyles1", exportParams.getNumberedListStyles());
        assertEquals(List.of("1", "2"), exportParams.getChapters());
        assertEquals(List.of("field1", "field2"), exportParams.getMetadataFields());
        assertEquals("de", exportParams.getLanguage());
        assertEquals("languageField", exportParams.getLanguageCustomField());
        assertEquals(List.of("role1", "role2"), exportParams.getLinkedWorkitemRoles());
        assertEquals("*.pdf", exportParams.getAttachmentsFilter());
        assertEquals("testcaseField", exportParams.getTestcaseFieldId());
    }

    @Test
    void testMissingChaptersMetadataFieldsAndQueryYieldNull() {
        StylePackageSettings stylePackageSettings = spy(new StylePackageSettings(settingsService));
        StylePackageModel stylePackage = stylePackageSettings.defaultValues();
        stylePackage.setSpecificChapters(null);
        stylePackage.setMetadataFields(null);
        stylePackage.setWorkItemsQuery("");
        doReturn(stylePackage).when(stylePackageSettings).read(any(), any(), any());
        NamedSettingsRegistry.INSTANCE.register(List.of(stylePackageSettings));

        ExportParams exportParams = jobUnit.getExportParams(module, new Arguments(Map.of()));
        assertNull(exportParams.getChapters());
        assertNull(exportParams.getMetadataFields());
        assertNull(exportParams.getUrlQueryParameters());
    }

    @Test
    void testSpecificStylePackageNotFound() {
        Arguments args = new Arguments(Map.of("style_package", "Specific"));
        NamedSettingsRegistry.INSTANCE.register(List.of(new StylePackageSettings(settingsService)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jobUnit.getExportParams(module, args));
        assertEquals("Style package 'Specific' is unavailable. Please contact system administrator.", exception.getMessage());
    }

    @Test
    void testLastBaselineUsed() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new StylePackageSettings(settingsService)));
        doReturn("271").when(jobUnit).getLastBaselineRevision(any());

        assertNull(jobUnit.getExportParams(module, new Arguments(Map.of())).getRevision());
        assertEquals("271", jobUnit.getExportParams(module, new Arguments(Map.of("prefer_last_baseline", "true"))).getRevision());
    }

    @Test
    void testWorkItemCreationFailedRequiredParametersNotProvided() {
        Arguments args = new Arguments(Map.of());
        ExportParams params = ExportParams.builder().projectId("projectId").build();

        UserFriendlyRuntimeException exception = assertThrows(UserFriendlyRuntimeException.class,
                () -> jobUnit.savePdfAsWorkItemAttachment(module, params, "TargetStatus", args, new byte[0], null));
        assertEquals("Workflow function isn't configured properly. Please contact system administrator.", exception.getMessage());
    }

    @Test
    void testNewWorkItemCreated() {
        ExportParams params = ExportParams.builder().projectId("projectId").build();
        IWorkItem newWorkItem = mock(IWorkItem.class);
        when(trackerProject.createWorkItem(anyString())).thenReturn(newWorkItem);
        IAttachment attachment = mock(IAttachment.class);
        when(newWorkItem.createAttachment(anyString(), anyString(), any())).thenReturn(attachment);
        when(module.getTitleWithSpace()).thenReturn("Some space / Document Title");
        doReturn("Status name").when(jobUnit).getStatusName(any(), anyString());
        doReturn("Attach Title.pdf").when(jobUnit).getDocumentFileName(params);

        jobUnit.savePdfAsWorkItemAttachment(module, params, "TargetStatus", new Arguments(Map.of("create_wi_type_id", "someType")), new byte[0], null);
        verify(pdfExporterPolarionService).getTrackerProject("projectId");
        verify(trackerProject).createWorkItem("someType");
        verify(newWorkItem).createAttachment(eq("Attach Title.pdf"), eq("Attach Title"), any());
        verify(newWorkItem).setTitle("Some space / Document Title -> Status name");
        verify(newWorkItem).setDescription(Text.html("This item was created automatically. Check 'Attachments' section for the generated PDF document."));
        verify(newWorkItem).save();
        verify(attachment).save();

        Arguments custom = new Arguments(Map.of("create_wi_type_id", "someType", "project_id", "proj1", "attachment_title", "Custom title", "create_wi_description", "Custom description"));
        jobUnit.savePdfAsWorkItemAttachment(module, params, "TargetStatus", custom, new byte[0], null);
        verify(pdfExporterPolarionService).getTrackerProject("proj1");
        verify(newWorkItem).createAttachment(eq("Attach Title.pdf"), eq("Custom title"), any());
        verify(newWorkItem).setDescription(Text.html("Custom description"));
    }

    @Test
    void testExistingWorkItemUsedAndOldAttachmentReplaced() {
        ExportParams params = ExportParams.builder().projectId("projectId").build();
        doReturn("Attach Title.pdf").when(jobUnit).getDocumentFileName(params);
        IWorkItem foundWorkItem = mock(IWorkItem.class);
        IAttachment oldAttachment = mock(IAttachment.class);
        IAttachment newAttachment = mock(IAttachment.class);
        when(foundWorkItem.getAttachmentByFileName("Attach Title.pdf")).thenReturn(oldAttachment);
        when(foundWorkItem.createAttachment(anyString(), anyString(), any())).thenReturn(newAttachment);
        when(pdfExporterPolarionService.getWorkItem("proj2", "ID-345")).thenReturn(foundWorkItem);

        // the work item id wins over the create type
        Arguments args = new Arguments(Map.of("project_id", "proj2", "create_wi_type_id", "someType"));
        jobUnit.savePdfAsWorkItemAttachment(module, params, "TargetStatus", args, new byte[0], "ID-345");

        verify(foundWorkItem).deleteAttachment(oldAttachment);
        verify(foundWorkItem).createAttachment(eq("Attach Title.pdf"), eq("Attach Title"), any());
        verify(newAttachment).save();
        verify(trackerProject, never()).createWorkItem(anyString());
    }
}
