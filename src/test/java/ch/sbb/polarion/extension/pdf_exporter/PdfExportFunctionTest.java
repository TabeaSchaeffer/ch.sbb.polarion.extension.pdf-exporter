package ch.sbb.polarion.extension.pdf_exporter;

import ch.sbb.polarion.extension.pdf_exporter.job.GeneratePdfJobUnit;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.alm.tracker.workflow.IArguments;
import com.polarion.alm.tracker.workflow.ICallContext;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobManager;
import com.polarion.platform.jobs.IJobService;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IJobUnitRepository;
import com.polarion.subterra.base.SubterraURI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfExportFunctionTest {

    @Mock
    IJobService jobService;
    @Mock
    IJobManager jobManager;
    @Mock
    IJobUnitRepository jobUnitRepository;
    @Mock
    IJobUnitFactory jobUnitFactory;
    @Mock
    IModule module;
    @Mock
    IArguments args;

    ICallContext<? extends IWorkflowObject> context;
    PdfExportFunction pdfExportFunction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        context = (ICallContext<? extends IWorkflowObject>) mock(ICallContext.class);
        lenient().when(context.getTarget()).thenAnswer((Answer<?>) invocation -> module);
        lenient().when(module.getProjectId()).thenReturn("project");
        lenient().when(module.getId()).thenReturn("Document");
        SubterraURI uri = mock(SubterraURI.class);
        lenient().when(uri.toString()).thenReturn("uri");
        lenient().when(module.getUri()).thenReturn(uri);
        lenient().when(jobService.getJobManager()).thenReturn(jobManager);
        lenient().when(jobService.getJobUnitRepository()).thenReturn(jobUnitRepository);
        pdfExportFunction = new PdfExportFunction(jobService);
    }

    @Test
    void testJobScheduled() throws Exception {
        when(jobUnitRepository.getJobUnitFactory(GeneratePdfJobUnit.JOB_NAME)).thenReturn(jobUnitFactory);
        GeneratePdfJobUnit jobUnit = mock(GeneratePdfJobUnit.class);
        when(jobUnitFactory.createJobUnit("GeneratePdfJobUnit for project/Document.")).thenReturn(jobUnit);
        IJob job = mock(IJob.class);
        when(jobManager.spawnJob(jobUnit, null)).thenReturn(job);

        pdfExportFunction.execute(context, args);

        verify(jobUnit).setArguments(args);
        verify(jobUnit).setContext(context);
        verify(jobUnit).setObjectUri("uri");
        verify(job).schedule();
    }

    @Test
    void testMissingJobFactoryIsReported() throws Exception {
        when(jobUnitRepository.getJobUnitFactory(GeneratePdfJobUnit.JOB_NAME)).thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pdfExportFunction.execute(context, args));
        assertEquals("The job " + GeneratePdfJobUnit.JOB_NAME + " is not available.", exception.getMessage());
        verify(jobManager, never()).spawnJob(any(), any());
    }

    @Test
    void testSchedulingErrorIsNotSwallowed() throws Exception {
        when(jobUnitRepository.getJobUnitFactory(GeneratePdfJobUnit.JOB_NAME)).thenReturn(jobUnitFactory);
        GeneratePdfJobUnit jobUnit = mock(GeneratePdfJobUnit.class);
        when(jobUnitFactory.createJobUnit(anyString())).thenReturn(jobUnit);
        when(jobManager.spawnJob(jobUnit, null)).thenThrow(new IllegalStateException("job manager down"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pdfExportFunction.execute(context, args));
        assertEquals("job manager down", exception.getMessage());
    }
}
