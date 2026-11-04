package ch.sbb.polarion.extension.pdf_exporter.job;

import com.polarion.platform.jobs.GenericJobException;
import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobUnit;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.spi.BasicJobDescriptor;
import com.polarion.platform.jobs.spi.JobParameterPrimitiveType;
import com.polarion.platform.jobs.spi.SimpleJobParameter;

/**
 * Creates job units for the job GeneratePdf.
 * @author Tabea Schaeffer
 * @since 2.0.0
 */
public class GeneratePdfJobUnitFactory implements IJobUnitFactory {
    
	/**
     * Create new job unit implementation
     * @param name Name of the job
     * @see com.polarion.platform.jobs.IJobUnitFactory#createJobUnit(java.lang.String)
     */
    public IJobUnit createJobUnit(String name) throws GenericJobException {
        return new GeneratePdfJobUnitImpl(name, this);
    }

    /**
     * Job Descriptor define parameters for job 
     * @param jobUnit IJobUnit object
     * @see com.polarion.platform.jobs.IJobUnitFactory#getJobDescriptor(com.polarion.platform.jobs.IJobUnit)
     */
    public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
        BasicJobDescriptor desc = new BasicJobDescriptor("Generate PDF Job", jobUnit);
        
        JobParameterPrimitiveType stringType = new JobParameterPrimitiveType("String", String.class);
        JobParameterPrimitiveType argsType = new JobParameterPrimitiveType("IArguments", String.class);
        JobParameterPrimitiveType contextType = new JobParameterPrimitiveType("ICallContext", String.class);

        
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "objectUri", "Object Uri", stringType).setRequired(true));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "arguments", "Arguments", argsType).setRequired(false));
        desc.addParameter(new SimpleJobParameter(desc.getRootParameterGroup(), "context", "Context", contextType).setRequired(false));
        return desc;
    }

    /**
     * Returns name for job implementation
     * @see com.polarion.platform.jobs.IJobUnitFactory#getName()
     */
    public String getName() {
        return GeneratePdfJobUnit.JOB_NAME;
    }
}
