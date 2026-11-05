package ch.sbb.polarion.extension.pdf_exporter.job;

import com.polarion.platform.jobs.IJobUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GeneratePdfJobUnitFactoryTest {

    private final GeneratePdfJobUnitFactory factory = new GeneratePdfJobUnitFactory();

    @Test
    void testName() {
        assertEquals(GeneratePdfJobUnit.JOB_NAME, factory.getName());
    }

    @Test
    void testJobDescriptor() {
        assertNotNull(factory.getJobDescriptor(mock(IJobUnit.class)));
    }
}
