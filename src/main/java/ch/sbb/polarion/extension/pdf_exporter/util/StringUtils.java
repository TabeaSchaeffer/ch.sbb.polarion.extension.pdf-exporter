package ch.sbb.polarion.extension.pdf_exporter.util;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkflowObject;
import com.polarion.core.util.exceptions.UserFriendlyRuntimeException;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;

/**
 * Resolves field placeholders in workflow function parameters.
 */
public class StringUtils {

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
    public static String replaceRegEx(String originalString, String patternString, IPObject object, boolean throwErrors) {
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
            Object value;

            switch (fieldId) {
                case "currentDate":
                    value = DateTimeFormatter.ISO_DATE.format(LocalDate.now());
                    break;
                case "projectId":
                    value = ((IUniqueObject) object).getProjectId();
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
            String replacementString = toReplacement(value, subOption);
            if (replacementString == null) {
                if (!throwErrors) {
                    replacedString = replacedString.replace(stringToReplace, "");
                    continue;
                }
                throw new UserFriendlyRuntimeException(value == null
                        ? String.format("The field '%s' does not exist or is empty.", fieldId)
                        : String.format("The field '%s' has the unsupported type '%s'.", fieldId, value.getClass().getSimpleName()));
            }
            replacedString = replacedString.replace(stringToReplace, replacementString);
        }
        return replacedString;
    }

    private static String toReplacement(Object value, String subOption) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Date date) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
        }
        if (value instanceof Text text) {
            return text.convertToHTML().getContent();
        }
        if (value instanceof IEnumOption option) {
            return "name".equals(subOption) ? option.getName() : option.getId();
        }
        if (value instanceof IPObjectList<?> list) {
            StringJoiner ids = new StringJoiner(",");
            for (Object item : list) {
                ids.add(((IPObject) item).getObjectId().getLocalId().getObjectName());
            }
            return ids.toString();
        }
        return null;
    }
}
