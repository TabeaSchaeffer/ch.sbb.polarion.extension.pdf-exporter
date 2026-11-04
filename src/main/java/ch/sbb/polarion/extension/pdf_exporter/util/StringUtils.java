package ch.sbb.polarion.extension.pdf_exporter.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.lang.Math;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.objecthunter.exp4j.ExpressionBuilder;

import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.projects.model.IFolder;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IApprovalStruct;
import com.polarion.alm.tracker.model.IBaseline;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IRow;
import com.polarion.alm.tracker.model.ITable;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.exceptions.UserFriendlyRuntimeException;
import com.polarion.core.util.logging.Logger;
import com.polarion.core.util.types.Text;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.persistence.spi.ModelTypedList;
import com.polarion.platform.persistence.IEnumOption;

/**
 * Utensils according to objects of type String.
 *
 * @author Markus Weber
 * @since 1.0.0
 * @see
 * <a href="https://polarion.weinmann.com/polarion/#/project/ToolValidation/workitem?id=TOOL-2851">TOOL-2851</a>
 */
public class StringUtils {

    /**
     * Checks if a String is a valid file path
     *
     * @param path Path to check
     * @return True if the file path is valid
     */
    public static boolean isValidFilePath(String path) {
        try {
            Paths.get(path);
            return true;
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Replaces a substring based on a regular expression.
     *
     * @param originalString Input String
     * @param patternString Pattern
     * @param object Object from where fields can be retrieved
     * @param throwErrors If true replacement errors are thrown. Otherwise null
     * is returned
     * @return replaced String
     */
    @SuppressWarnings("unchecked")
    public static String replaceRegEx(String originalString, String patternString, IPObject object, boolean throwErrors) {
        ITrackerService trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);
        String replacedString = originalString;
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(originalString);
        while (matcher.find()) {
            String stringToReplace = matcher.group(0);
            String fieldId = matcher.group(1);
            String subOption = matcher.group(2);
            if (subOption == null) {
                subOption = "";
            }
            Object value = null;

            switch (fieldId) {
                case "baselineCount":
                    // Get count of baselines even if the document was moved
                    String latestRevision = trackerService.getDataService().getLastRevisionCommitedByCurrentThread();
                    if (latestRevision == null) {
                        latestRevision = trackerService.getDataService().getLastStorageRevision().getName();
                    }
                    IModule module = (IModule) object;
                    IPObjectList<IModule> revisionsDocument = trackerService.getDataService().getObjectHistory(module);
                    IModule revisionDocument = null;
                    ArrayList<IBaseline> baselines = new ArrayList<IBaseline>();
                    for (int i = 0; i < revisionsDocument.size(); i++) {
                        IModule newRevisionDocument = revisionsDocument.get(i);
                        if (revisionDocument == null || newRevisionDocument.getRelativePath() != revisionDocument.getRelativePath()) {
                            String query = "project.id:" + newRevisionDocument.getProjectId()
                                    + " AND baseObject:\"document/" + newRevisionDocument.getRelativePath()
                                    + "\" AND baseRevision:[" + newRevisionDocument.getRevision() + " TO " + latestRevision + "]";
                            IPObjectList<IBaseline> newBaselines = trackerService.getDataService().searchInstancesInBaseline("Baseline", query, "baseRevision", latestRevision);
                            for (int j = 0; j < newBaselines.size(); j++) {
                                if (newBaselines.get(j).isUnresolvable() == false
                                        && baselines.contains(newBaselines.get(j)) == false
                                        && (newBaselines.get(j).getDescriptionText() == null || newBaselines.get(j).getDescriptionText().getContent().contains("NO_HISTORY_ENTRY") == false)
                                        && newBaselines.get(j).getName().contains("NO_HISTORY_ENTRY") == false) {
                                    baselines.add(newBaselines.get(j));
                                }
                            }
                            revisionDocument = newRevisionDocument;
                        }
                    }
                    value = baselines.size();
                    break;
                case "currentDate":
                    value = DateTimeFormatter.ISO_DATE.format(LocalDate.now());
                    break;
                case "oldRevisionHistoryEntries":
                    if (subOption.equals("count")) {
                        // Get count of table rows 
                        int count = 0;
                        if (object.getCustomField("oldRevisionHistoryEntries") != null) {
                            List<IRow> tableRows = ((ITable) object.getCustomField("oldRevisionHistoryEntries")).getRows();
                            if (!tableRows.isEmpty()) {
                                for (IRow row : tableRows) {
                                    List<Text> rowValues = (List<Text>) row.getValues();
                                    if (!rowValues.get(4).convertToPlainText().getContent().trim().isEmpty()) {
                                        try {
                                            count = Math.max(count, (int) Math.floor(Double.parseDouble(rowValues.get(4).convertToPlainText().getContent().trim())));
                                        } catch (Exception e) {
                                            // Do not increase the count, since it is probably a string
                                        }
                                    } else {
                                        count += 1;
                                    }
                                }
                            }
                        }
                        value = count;
                    } else {
                        throw new UserFriendlyRuntimeException(String.format("The field oldRevisionHistoryEntries is currently only supported with the subOption 'count', but has subOption '%s'.", subOption));
                    }
                    break;
                case "projectId":
                    value = ((IUniqueObject) object).getProjectId();
                    break;
                case "revision":
                    value = trackerService.getDataService().getLastRevisionCommitedByCurrentThread();
                    if (value == null) {
                        value = trackerService.getDataService().getLastStorageRevision().getName();
                    }
                    value = Integer.valueOf((String) value) + 1;
                    break;
                case "rootFolder":
                    if (object instanceof IModule) {
                        IFolder currentFolder = ((IModule) object).getFolder();
                        while (currentFolder.getParent() != null) {
                            currentFolder = currentFolder.getParent();
                        }
                        value = currentFolder.getName();
                    } else {
                        throw new UserFriendlyRuntimeException("The field 'rootFolder' is currently only supported for objects of type IModule.");
                    }
                    break;
                case "space":
                    if (object instanceof IModule) {
                        value = ((IModule) object).getModuleFolder();
                    } else {
                        throw new UserFriendlyRuntimeException("The field 'space' is currently only supported for objects of type IModule.");
                    }
                    break;
                case "title":
                    if (object instanceof IModule) {
                        value = ((IModule) object).getTitleOrName();
                    } else {
                        value = object.getValue(fieldId);
                    }
                    break;
                case "type":
                    value = ((IWorkflowObject) object).getType();
                    break;
                default:
                    value = object.getValue(fieldId);
                    break;
            }
            String replacementString = "";
            if (value == null) {
                if (!throwErrors) {
                    replacedString = replacedString.replace(stringToReplace, replacementString);
                    continue;
                }
                throw new UserFriendlyRuntimeException(String.format("The field '%s' does not exist or is empty.", fieldId));
            }

            switch (value.getClass().getSimpleName()) {
                case "Date":
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    replacementString = String.valueOf(df.format(((Date) value).getTime()));
                    break;
                case "CustomTypedList":
                    replacementString = value.toString();
                    break;
                case "Integer":
                    replacementString = value.toString();
                    break;
                case "String":
                    replacementString = (String) value;
                    break;
                case "Text":
                    replacementString = ((Text) value).convertToHTML().getContent();
                    break;
                case "TypeOpt":
                case "EnumOption":
                    switch (subOption) {
                        case "name":
                            replacementString = ((IEnumOption) value).getName();
                            break;
                        default:
                            replacementString = ((IEnumOption) value).getId();
                    }
                    break;
                case "PObjectTypedList":
                    IPObjectList<IPObject> objectList = (IPObjectList<IPObject>) value;
                    for (int index = 0; index < objectList.size(); index++) {
                        IPObject valueObject = objectList.get(index);
                        String id = valueObject.getObjectId().getLocalId().getObjectName();
                        if (replacementString == "") {
                            replacementString = id;
                        } else {
                            replacementString += "," + id;
                        }
                    }
                    break;
                case "ModelTypedList":
                    ModelTypedList<IApprovalStruct> approvalList = (ModelTypedList<IApprovalStruct>) value;
                    for (int index = 0; index < approvalList.size(); index++) {
                        IApprovalStruct valueObject = approvalList.get(index);
                        String id = valueObject.getUser().getId();
                        if (replacementString == "") {
                            replacementString = id;
                        } else {
                            replacementString += "," + id;
                        }
                    }
                    break;
                default:
                    if (!throwErrors) {
                        replacedString = replacedString.replace(stringToReplace, replacementString);
                        continue;
                    }
                    throw new UserFriendlyRuntimeException(String.format("A string should contain information of the field '%s', but the type '%s' is not supported in 'StringUtils'. Please contact a Polarion administrator.",
                            fieldId, value.getClass().getSimpleName()));
            }
            replacedString = replacedString.replace(stringToReplace, replacementString);
        }
        return replacedString;
    }

    /**
     * Executes a calculation formulated as a String and returns the final value
     * as a String
     *
     * @param originalString String
     * @param calcPattern Specifies which part should be considered as a
     * calculation
     * @return Replaced String
     */
    public static String calcExpressions(String originalString, String calcPattern) {
        String replacedString = originalString;
        Pattern pattern = Pattern.compile(calcPattern);
        Matcher matcher = pattern.matcher(originalString);
        while (matcher.find()) {
            String stringToReplace = matcher.group(0);
            String replacementString = String.valueOf(new ExpressionBuilder(matcher.group(1)).build().evaluate());
            Logger logger = Logger.getLogger("DEBUG");
            logger.error("DEBUG: Found '" + stringToReplace + "', replaced with: '" + replacementString + "'");
            replacedString = replacedString.replace(stringToReplace, replacementString);
        }
        return replacedString;
    }
}
