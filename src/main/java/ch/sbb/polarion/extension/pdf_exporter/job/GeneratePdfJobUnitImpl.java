package ch.sbb.polarion.extension.pdf_exporter.job;

import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.internal.baseline.BaseObjectBaselinesSearch;
import com.polarion.alm.tracker.model.IAttachment;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.model.ipi.IInternalBaselinesManager;
import com.polarion.alm.tracker.workflow.IArguments;
import com.polarion.alm.tracker.workflow.ICallContext;
import com.polarion.core.util.exceptions.UserFriendlyRuntimeException;
import com.polarion.core.util.types.Text;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.UnresolvableObjectException;
import com.polarion.platform.persistence.WrapperException;
import com.polarion.portal.internal.shared.navigation.ProjectScope;
import com.polarion.portal.server.PObjectDataProvider;
import com.polarion.subterra.base.SubterraURI;
import com.polarion.subterra.base.data.model.internal.EnumType;
import com.polarion.subterra.base.location.Location;

import ch.sbb.polarion.extension.generic.exception.ObjectNotFoundException;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import ch.sbb.polarion.extension.pdf_exporter.converter.PdfConverter;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.DocumentType;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.ExportParams;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.Orientation;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.conversion.PaperSize;
import ch.sbb.polarion.extension.pdf_exporter.rest.model.settings.stylepackage.StylePackageModel;
import ch.sbb.polarion.extension.pdf_exporter.service.PdfExporterPolarionService;
import ch.sbb.polarion.extension.pdf_exporter.settings.StylePackageSettings;
import ch.sbb.polarion.extension.pdf_exporter.util.DocumentFileNameHelper;
import ch.sbb.polarion.extension.pdf_exporter.util.StringUtils;

/**
 * Implementation of the GeneratePdf job 
 * @author Tabea Schaeffer
 * @since 2.0.0
 */
public class GeneratePdfJobUnitImpl extends AbstractJobUnit implements GeneratePdfJobUnit {
	private static final String PARAM_NAME_PROJECT_ID = "project_id";
	private static final String PARAM_NAME_EXISTING_WORK_ITEM_ID = "existing_wi_id";
	private static final String PARAM_NAME_CREATE_WORK_ITEM_TYPE = "create_wi_type_id";
	private static final String PARAM_NAME_CREATE_WORK_ITEM_TITLE = "create_wi_title";
	private static final String PARAM_NAME_CREATE_WORK_ITEM_DESCRIPTION = "create_wi_description";
	private static final String PARAM_NAME_ATTACHMENT_TITLE = "attachment_title";
	private static final String PARAM_NAME_STYLE_PACKAGE = "style_package";
	private static final String PARAM_NAME_PREFER_LAST_BASELINE = "prefer_last_baseline";
	public static final String WF_COPY_WORKITEM_PATTERN_FIELD = "\\{wi:(\\w+)\\.?(\\w+)?\\}";
	public static final String WF_COPY_DOCUMENT_PATTERN_FIELD = "\\{doc:(\\w+)\\.?(\\w+)?\\}";
	
	private static final String STYLE_PACKAGE_DEFAULT = "Default";
	private final PdfExporterPolarionService pdfExporterPolarionService;
	private final PdfConverter pdfConverter;
    private ITrackerService trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);
    private IDataService dataService = trackerService.getDataService();
	

    private String objectUri;
    private IArguments arguments;
    private ICallContext context;

	/**
	 * Constructor
	 * @param name Name of the Job
	 * @param creator Factory of the job
	 */
    public GeneratePdfJobUnitImpl(String name, IJobUnitFactory creator) {
        super(name, creator);
        this.pdfExporterPolarionService = new PdfExporterPolarionService();
	    this.pdfConverter = new PdfConverter();
    }
    


    @Override
    public void setObjectUri(String objectUri) {
        this.objectUri = objectUri;
    }
    
    public void setContext (ICallContext context) {
    	this.context= context;
    }
    
    @Override
    public void setArguments(IArguments arguments) {
        this.arguments = arguments;
    }

    /**
     * The main job method
     * @param progress IProgressMonitor object
     */
    protected IJobStatus runInternal(IProgressMonitor progress) {
        progress.beginTask(getName(), 0);
        try {
        		IWorkflowObject object = (IWorkflowObject) dataService.getInstance(SubterraURI.fromString(objectUri));
        		
            	String existingWorkItemId = arguments.getAsString(PARAM_NAME_EXISTING_WORK_ITEM_ID, null); 
                String WF_COPY_WORKITEM_PATTERN_FIELD = "\\{wi:(\\w+)\\.?(\\w+)?\\}";
                existingWorkItemId = StringUtils.replaceRegEx(existingWorkItemId, WF_COPY_WORKITEM_PATTERN_FIELD, object, false);

            	IModule module = resolveModule(object, existingWorkItemId);
        		String workItemId = resolveWorkItemId(object, existingWorkItemId);
        		

                // newly created modules don't have lastRevision and throw UnresolvableObjectException
                // we are going to skip this case entirely, doubt anyone wants to export document that has been just created (thus it is basically empty)
                try {
                    module.getLastRevision();
                } catch (UnresolvableObjectException | WrapperException e) {
                    return null;
                }
                ExportParams exportParams = getExportParams(module, arguments);
                byte[] pdfBytes = pdfConverter.convertToPdf(exportParams, null);
                TransactionalExecutor.executeInWriteTransaction(tx -> {
                	savePdfAsWorkItemAttachment(module, exportParams, context.getTargetStatusId(), arguments, pdfBytes, workItemId);
                return null;
                });
            return getStatusOK(null);
		} catch(Exception e) {
			getLogger().error(String.format("Exception caught: %s", e.getLocalizedMessage()));
            return getStatusFailed(e.getLocalizedMessage(), e);
        } finally {
            progress.done();
        }
    }
    
    private IModule resolveModule(IWorkflowObject object, String pdfPath) {
        try {
            ITrackerService trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);

            if (object instanceof IModule module) {
                return module;
            } else if (object instanceof IWorkItem workItem) {
                String projectId = workItem.getProjectId();
                String space = StringUtils.replaceRegEx(pdfPath, WF_COPY_WORKITEM_PATTERN_FIELD, object, false);
                IModule module = trackerService.getModuleManager().getModule(trackerService.getProjectsService().getProject(projectId), Location.getLocation(space).append(workItem.getId()));
                return module;
            }
        } catch (Exception e) {
        }
        return null;
    }

    private String resolveWorkItemId(IWorkflowObject object, String pdfPath) {
        if (object instanceof IWorkItem workItem) {
            return workItem.getId();
        } else if (object instanceof IModule module) {
            return StringUtils.replaceRegEx(pdfPath, WF_COPY_DOCUMENT_PATTERN_FIELD, module, false);
        }
        return null;
    }

    @VisibleForTesting
    @SuppressWarnings("java:S3252") // allow building ExportParams using its own builder
    ExportParams getExportParams(IModule module, @NotNull IArguments args) {
        String projectId = module.getProjectId();
        String stylePackageName = args.getAsString(PARAM_NAME_STYLE_PACKAGE, STYLE_PACKAGE_DEFAULT);

        String revision = null;
        if (args.getAsBoolean(PARAM_NAME_PREFER_LAST_BASELINE, false)) {
            revision = getLastBaselineRevision(module);
        }

        StylePackageModel stylePackage;
        try {
            stylePackage = ((StylePackageSettings) NamedSettingsRegistry.INSTANCE.getByFeatureName(StylePackageSettings.FEATURE_NAME))
                    .read(ScopeUtils.getScopeFromProject(projectId), SettingId.fromName(stylePackageName), null);
        } catch (ObjectNotFoundException notFoundException) {
            throw new IllegalArgumentException("Styled package '%s' is unavailable. Please contact system administrator.".formatted(stylePackageName));
        }

        return ExportParams.builder()
                .projectId(projectId)
                .locationPath(module.getModuleLocation().getLocationPath())
                .revision(revision)
                .documentType(DocumentType.LIVE_DOC)
                .coverPage(stylePackage.getCoverPage())
                .css(stylePackage.getCss())
                .headerFooter(stylePackage.getHeaderFooter())
                .localization(stylePackage.getLocalization())
                .webhooks(stylePackage.getWebhooks())
                .headersColor(stylePackage.getHeadersColor())
                .orientation(Orientation.valueOf(stylePackage.getOrientation()))
                .paperSize(PaperSize.valueOf(stylePackage.getPaperSize()))
                .fitToPage(stylePackage.isFitToPage())
                .renderComments(stylePackage.getRenderComments())
                .watermark(stylePackage.isWatermark())
                .markReferencedWorkitems(stylePackage.isMarkReferencedWorkitems())
                .cutEmptyChapters(stylePackage.isCutEmptyChapters())
                .cutEmptyWIAttributes(stylePackage.isCutEmptyWorkitemAttributes())
                .cutLocalUrls(stylePackage.isCutLocalURLs())
                .followHTMLPresentationalHints(stylePackage.isFollowHTMLPresentationalHints())
                .numberedListStyles(stylePackage.getCustomNumberedListStyles())
                .chapters(stylePackage.getSpecificChapters() == null ? null : List.of(stylePackage.getSpecificChapters().split(",")))
                .language(stylePackage.getLanguage())
                .linkedWorkitemRoles(stylePackage.getLinkedWorkitemRoles())
                .attachmentsFilter(stylePackage.getAttachmentsFilter())
                .testcaseFieldId(stylePackage.getTestcaseFieldId())
                .build();
    }

    @VisibleForTesting
    void savePdfAsWorkItemAttachment(IModule module, ExportParams exportParams, String targetStatusId, IArguments args, byte[] pdfContentBytes, String workItemId) {
        String workItemProjectId = Objects.requireNonNull(args.getAsString(PARAM_NAME_PROJECT_ID, exportParams.getProjectId()));
        
        String createWorkItemType = args.getAsString(PARAM_NAME_CREATE_WORK_ITEM_TYPE, null);
       
        IWorkItem workItem;
        if (workItemId != null) {
            workItem = pdfExporterPolarionService.getWorkItem(workItemProjectId, workItemId);
        } else if (createWorkItemType != null) {
            workItem = pdfExporterPolarionService.getTrackerProject(workItemProjectId).createWorkItem(createWorkItemType);
            workItem.setTitle(args.getAsString(PARAM_NAME_CREATE_WORK_ITEM_TITLE, "%s -> %s".formatted(module.getTitleWithSpace(), getStatusName(module, targetStatusId))));
            workItem.setDescription(Text.html(args.getAsString(PARAM_NAME_CREATE_WORK_ITEM_DESCRIPTION, "This item was created automatically. Check 'Attachments' section for the generated PDF document.")));
            workItem.save();
        } else {
            throw new UserFriendlyRuntimeException("Workflow function isn't configured properly. Please contact system administrator.");
        }
        
        String attachmentFileName = getDocumentFileName(exportParams);
        IAttachment existing = workItem.getAttachmentByFileName(attachmentFileName);
        if (existing != null) {
            workItem.deleteAttachment(existing);
        }

        String attachmentTitle = args.getAsString(PARAM_NAME_ATTACHMENT_TITLE, attachmentFileName.replaceAll("\\.pdf$", ""));
        IAttachment attachment = workItem.createAttachment(attachmentFileName, attachmentTitle, new ByteArrayInputStream(pdfContentBytes));
        attachment.save();
    }

    @VisibleForTesting
    String getLastBaselineRevision(IModule module) {
        IInternalBaselinesManager baselinesManager = (IInternalBaselinesManager) pdfExporterPolarionService.getTrackerService().getTrackerProject(module.getProject().getId()).getBaselinesManager();
        return new BaseObjectBaselinesSearch(module, baselinesManager)
                .includeProjectBaselines(true).resolve(true).execute().stream()
                .map(b -> Long.valueOf(b.getBaseRevision()))
                .max(Comparator.naturalOrder()).map(String::valueOf).orElse(null);
    }

    @VisibleForTesting
    String getStatusName(IModule module, String targetStatusId) {
        IEnumeration<?> enumeration = pdfExporterPolarionService.getTrackerService().getDataService().getEnumerationForEnumId(
                new EnumType(Objects.requireNonNull(module.getStatus()).getEnumId()),
                PObjectDataProvider.scopeToContextId(new ProjectScope(module.getProjectId())));
        return enumeration.getAllOptions().stream().filter(e -> Objects.equals(e.getId(), targetStatusId)).map(IEnumOption::getName).findFirst().orElse(targetStatusId);
    }

    @VisibleForTesting
    String getDocumentFileName(ExportParams exportParams) {
        return new DocumentFileNameHelper().getDocumentFileName(exportParams);
    }
}

                    
     
