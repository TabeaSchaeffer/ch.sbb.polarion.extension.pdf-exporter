package ch.sbb.polarion.extension.pdf_exporter.job;


import com.polarion.alm.tracker.workflow.IArguments;
import com.polarion.alm.tracker.workflow.ICallContext;
import com.polarion.platform.jobs.IJobUnit;

/**
 * Defines the functions used for the job GeneratePdf.
 * This job is responsible for generating a PDF from a Work Item and sending an email with the PDF attached.
 * 
 * @author Tabea Schaeffer
 * @since 2.0.0
 */
public interface GeneratePdfJobUnit extends IJobUnit {

    /** 
     * Job name for GeneratePdf.
     */
    static final String JOB_NAME = "GeneratePdf.job";

    /**
     * Sets the required job parameter: objectUri.
     * @param objectUri object as SubterraURI String.
     */
    void setObjectUri(String objectUri);
  
    /**
     * Sets the args.
     * @param args TODO.
     */
    void setArguments(IArguments args);
    
    void setContext(ICallContext context);
}
