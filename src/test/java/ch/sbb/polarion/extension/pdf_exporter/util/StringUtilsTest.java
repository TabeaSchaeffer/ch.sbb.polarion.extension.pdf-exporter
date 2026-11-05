package ch.sbb.polarion.extension.pdf_exporter.util;

import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.exceptions.UserFriendlyRuntimeException;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StringUtilsTest {

    private static final String WI_PATTERN = "\\{wi:(\\w+)\\.?(\\w+)?\\}";

    @Test
    void testTextWithoutPlaceholderIsUnchanged() {
        assertEquals("plain", StringUtils.replaceRegEx("plain", WI_PATTERN, mock(IWorkItem.class), true));
    }

    @Test
    void testStringAndNumberValues() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getValue("ref")).thenReturn("WI-1");
        when(workItem.getValue("count")).thenReturn(42);

        assertEquals("WI-1/42", StringUtils.replaceRegEx("{wi:ref}/{wi:count}", WI_PATTERN, workItem, true));
    }

    @Test
    void testDateValue() throws Exception {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getValue("due")).thenReturn(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-01-02 03:04:05"));

        assertEquals("2026-01-02 03:04:05", StringUtils.replaceRegEx("{wi:due}", WI_PATTERN, workItem, true));
    }

    @Test
    void testTextValue() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getValue("description")).thenReturn(Text.html("<b>bold</b>"));

        assertEquals("<b>bold</b>", StringUtils.replaceRegEx("{wi:description}", WI_PATTERN, workItem, true));
    }

    @Test
    void testEnumValueIdAndName() {
        IWorkItem workItem = mock(IWorkItem.class);
        IEnumOption option = mock(IEnumOption.class);
        when(option.getId()).thenReturn("high");
        when(option.getName()).thenReturn("High");
        when(workItem.getValue("severity")).thenReturn(option);

        assertEquals("high", StringUtils.replaceRegEx("{wi:severity}", WI_PATTERN, workItem, true));
        assertEquals("High", StringUtils.replaceRegEx("{wi:severity.name}", WI_PATTERN, workItem, true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testObjectListValue() {
        IWorkItem workItem = mock(IWorkItem.class);
        IPObject first = mock(IPObject.class, Answers.RETURNS_DEEP_STUBS);
        IPObject second = mock(IPObject.class, Answers.RETURNS_DEEP_STUBS);
        when(first.getObjectId().getLocalId().getObjectName()).thenReturn("WI-1");
        when(second.getObjectId().getLocalId().getObjectName()).thenReturn("WI-2");
        IPObjectList<IPObject> list = mock(IPObjectList.class);
        when(list.iterator()).thenReturn(List.of(first, second).iterator());
        when(workItem.getValue("links")).thenReturn(list);

        assertEquals("WI-1,WI-2", StringUtils.replaceRegEx("{wi:links}", WI_PATTERN, workItem, true));
    }

    @Test
    void testBuiltInFields() {
        IModule module = mock(IModule.class);
        when(module.getProjectId()).thenReturn("project");
        when(module.getModuleFolder()).thenReturn("Space");
        when(module.getTitleOrName()).thenReturn("Document");
        String today = DateTimeFormatter.ISO_DATE.format(LocalDate.now());

        assertEquals("project/Space/Document/" + today,
                StringUtils.replaceRegEx("{wi:projectId}/{wi:space}/{wi:title}/{wi:currentDate}", WI_PATTERN, module, true));
    }

    @Test
    void testSpaceOnlySupportedForModules() {
        IWorkItem workItem = mock(IWorkItem.class);

        assertThrows(UserFriendlyRuntimeException.class, () -> StringUtils.replaceRegEx("{wi:space}", WI_PATTERN, workItem, false));
    }

    @Test
    void testEmptyValue() {
        IWorkItem workItem = mock(IWorkItem.class);

        assertEquals("a-b", StringUtils.replaceRegEx("a-{wi:missing}b", WI_PATTERN, workItem, false));
        UserFriendlyRuntimeException exception = assertThrows(UserFriendlyRuntimeException.class,
                () -> StringUtils.replaceRegEx("{wi:missing}", WI_PATTERN, workItem, true));
        assertEquals("The field 'missing' does not exist or is empty.", exception.getMessage());
    }

    @Test
    void testUnsupportedValueType() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getValue("flag")).thenReturn(Boolean.TRUE);

        assertEquals("", StringUtils.replaceRegEx("{wi:flag}", WI_PATTERN, workItem, false));
        UserFriendlyRuntimeException exception = assertThrows(UserFriendlyRuntimeException.class,
                () -> StringUtils.replaceRegEx("{wi:flag}", WI_PATTERN, workItem, true));
        assertEquals("The field 'flag' has the unsupported type 'Boolean'.", exception.getMessage());
    }
}
