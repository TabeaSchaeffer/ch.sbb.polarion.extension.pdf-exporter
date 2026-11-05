package ch.sbb.polarion.extension.pdf_exporter;

import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.workflow.IArguments;
import com.polarion.alm.tracker.workflow.ICallContext;
import com.polarion.alm.tracker.workflow.IFunction;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.jobs.GenericJobException;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobManager;

import ch.sbb.polarion.extension.pdf_exporter.job.GeneratePdfJobUnit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import com.polarion.platform.jobs.IJobService;
import com.polarion.platform.jobs.IJobUnitFactory;

public class PdfExportFunction implements IFunction<IModule> {

    private final IJobService jobService;

    public PdfExportFunction() {
        this(PlatformContext.getPlatform().lookupService(IJobService.class));
    }

    @VisibleForTesting
    PdfExportFunction(IJobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public void execute(@NotNull ICallContext context, @NotNull IArguments args) {
        IWorkflowObject object = context.getTarget();
        String jobUnitName = String.format("GeneratePdfJobUnit for %s/%s.", object.getProjectId(), object.getId());
        IJobManager jobManager = jobService.getJobManager();
        IJobUnitFactory jobFactory = jobService.getJobUnitRepository().getJobUnitFactory(GeneratePdfJobUnit.JOB_NAME);
        if (jobFactory == null) {
            throw new IllegalStateException(String.format("The job %s is not available.", GeneratePdfJobUnit.JOB_NAME));
        }

        try {
            GeneratePdfJobUnit jobUnit = (GeneratePdfJobUnit) jobFactory.createJobUnit(jobUnitName);
            jobUnit.setArguments(args);
            jobUnit.setContext(context);
            jobUnit.setObjectUri(object.getUri().toString());
            IJob job = jobManager.spawnJob(jobUnit, null);
            job.schedule();
        } catch (GenericJobException e) {
            throw new IllegalStateException("Scheduling of %s failed: %s".formatted(jobUnitName, e.getMessage()), e);
        }
    }

}
