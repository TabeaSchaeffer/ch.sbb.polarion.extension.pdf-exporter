package ch.sbb.polarion.extension.pdf_exporter;

import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.workflow.IArguments;
import com.polarion.alm.tracker.workflow.ICallContext;
import com.polarion.alm.tracker.workflow.IFunction;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobManager;

import ch.sbb.polarion.extension.pdf_exporter.job.GeneratePdfJobUnit;

import com.polarion.core.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import com.polarion.platform.jobs.IJobService;
import com.polarion.platform.jobs.IJobUnitFactory;

public class PdfExportFunction implements IFunction<IModule> {

    private final IJobService jobService = PlatformContext.getPlatform().lookupService(IJobService.class);
    private final Logger logger = Logger.getLogger(PdfExportFunction.class);

    @Override
    public void execute(@NotNull ICallContext context, @NotNull IArguments args) {
        IWorkflowObject object = context.getTarget();
        String jobUnitName = String.format("GeneratePdfJobUnit for WorkItem %s/%s.", object.getProjectId(), object.getId());
        IJobManager jobManager = jobService.getJobManager();
        IJobUnitFactory jobFactory = jobService.getJobUnitRepository().getJobUnitFactory(GeneratePdfJobUnit.JOB_NAME);
        if (jobFactory == null) {
            logger.error(String.format("The job %s is not available.", GeneratePdfJobUnit.JOB_NAME));
            return;
        }

        try {
            GeneratePdfJobUnit jobUnit = (GeneratePdfJobUnit) jobFactory.createJobUnit(jobUnitName);
            jobUnit.setArguments(args);
            jobUnit.setContext(context);
            jobUnit.setObjectUri(object.getUri().toString());
            IJob job = jobManager.spawnJob(jobUnit, null);
            job.schedule();

        } catch (Exception e) {
            logger.error(e);
        }

    }

}
