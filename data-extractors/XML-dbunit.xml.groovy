/*
 * Available context bindings:
 *   COLUMNS     List<DataColumn>
 *   ROWS        Iterable<DataRow>
 *   OUT         { append() }
 *   FORMATTER   { format(row, col); formatValue(Object, col); getTypeName(Object, col); isStringLiteral(Object, col); }
 *   TRANSPOSED  Boolean
 * plus ALL_COLUMNS, TABLE, DIALECT
 *
 * where:
 *   DataRow     { rowNumber(); first(); last(); data(): List<Object>; value(column): Object }
 *   DataColumn  { columnNumber(), name() }
 */


import com.intellij.openapi.util.text.StringUtil

import java.util.regex.Pattern

NEWLINE = System.getProperty("line.separator")
INDENT = "    "

pattern = Pattern.compile("[^\\w\\d]")

def escapeTag(name) {
    name = pattern.matcher(name).replaceAll("_")
    return name.isEmpty() || !Character.isLetter(name.charAt(0)) ? "_$name" : name
}

def printRow(level, rowTag, values) {
    if (level == 0) {
        OUT.append("$NEWLINE$INDENT<$rowTag$NEWLINE")
    }
    values.each { name, col, valuesName, value ->
        switch (value) {
            case Map:
                def mapValues = new ArrayList<Tuple>()
                value.each { key, v -> mapValues.add(new Tuple(escapeTag(key.toString()), col, key.toString(), v)) }
                printRow(level + 1, name, mapValues)
                break
            case Object[]:
            case Iterable:
                def listItems = new ArrayList<Tuple>()
                def itemName = valuesName != null ? escapeTag(StringUtil.unpluralize(valuesName) ?: "item") : "item"
                value.collect { v -> listItems.add(new Tuple(itemName, col, null, v)) }
                printRow(level + 1, name, listItems)
                break
            default:
                if (value == null) {
                    break //null values are just omitted
                } else {
                    def formattedValue = FORMATTER.formatValue(value, col)
                    if (!isXmlString(formattedValue)) {
                        formattedValue = StringUtil.escapeXmlEntities(formattedValue)
                    }
                    OUT.append(/${INDENT * 2}$name="$formattedValue"$NEWLINE/)
                }
        }
    }
    OUT.append("$INDENT/>")
}

def isXmlString(string) {
    return string.startsWith("<") && string.endsWith(">") && (string.contains("</") || string.contains("/>"))
}

OUT.append(
        """<?xml version="1.0" encoding="UTF-8"?>
<dataset>""")

if (!TRANSPOSED) {
    def rowTag = TABLE?.getName() ?: "My_Table"
    ROWS.each { row ->
        def values = COLUMNS
                .findAll { col -> row.hasValue(col) }
                .collect { col ->
                    new Tuple(escapeTag(col.name()), col, col.name(), row.value(col))
                }
        printRow(0, rowTag, values)
    }
} else {
    def values = COLUMNS.collect { new ArrayList<Tuple>() }
    ROWS.eachWithIndex { row, rowIdx ->
        COLUMNS.eachWithIndex { col, colIdx ->
            if (row.hasValue(col)) {
                def value = row.value(col)
                values[colIdx].add(new Tuple("value", col, col.name(), value))
            }
        }
    }
    values.eachWithIndex { it, index ->
        printRow(0, escapeTag(COLUMNS[index].name()), it)
    }
}

OUT.append("""
</dataset>
""")
