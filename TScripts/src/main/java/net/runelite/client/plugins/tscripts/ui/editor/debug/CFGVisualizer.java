package net.runelite.client.plugins.tscripts.ui.editor.debug;

import com.mxgraph.canvas.mxGraphics2DCanvas;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.shape.mxRectangleShape;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import net.runelite.client.plugins.tscripts.adapter.models.variable.ArrayAccess;
import net.runelite.client.plugins.tscripts.util.HashUtil;
import net.runelite.client.plugins.tscripts.sevices.eventbus.TEventBus;
import net.runelite.client.plugins.tscripts.sevices.eventbus._Subscribe;
import net.runelite.client.plugins.tscripts.sevices.eventbus.events.CurrentInstructionChanged;
import net.runelite.client.plugins.tscripts.sevices.eventbus.events.ScriptStateChanged;
import net.runelite.client.plugins.tscripts.adapter.models.method.MethodCall;
import net.runelite.client.plugins.tscripts.adapter.models.Scope.Scope;
import net.runelite.client.plugins.tscripts.adapter.models.condition.Condition;
import net.runelite.client.plugins.tscripts.adapter.models.condition.ConditionType;
import net.runelite.client.plugins.tscripts.adapter.models.condition.Conditions;
import net.runelite.client.plugins.tscripts.adapter.models.Element;
import net.runelite.client.plugins.tscripts.adapter.models.ElementType;
import net.runelite.client.plugins.tscripts.adapter.models.variable.AssignmentType;
import net.runelite.client.plugins.tscripts.adapter.models.variable.VariableAssignment;
import net.runelite.client.plugins.tscripts.runtime.Runtime;
import net.runelite.client.plugins.tscripts.util.TextUtil;
import net.runelite.client.plugins.tscripts.util.controlflow.*;
import net.runelite.client.plugins.tscripts.util.iterators.AlphabetIterator;
import net.runelite.client.plugins.tscripts.util.iterators.NumericIterator;
import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Visualizes the control flow graph of a given Script
 */
public class CFGVisualizer extends JPanel {
    private mxGraph graph;
    private Object parent;
    private final Map<String, Object> nodesMap = new HashMap<>();
    private final Map<String,String> edgeLabels = new HashMap<>();
    private final AlphabetIterator alphabetIterator = new AlphabetIterator("");
    private final NumericIterator numericIterator = new NumericIterator();
    private final ScopeStack scopeStack = new ScopeStack();
    private int nodeCounter = 0;
    private final Map<Object,String> linkBacks = new HashMap<>();
    private String scriptName;
    private Runtime runtime;
    private boolean isCurrent = false;
    private double scale = 1.0;
    private long lastUpdateTime = 0;

    public static CFGVisualizer create(Runtime runtime, Scope scope, String name) {
        CFGVisualizer panel = new CFGVisualizer(runtime, scope, name);
        panel.setVisible(true);
        return panel;
    }

    private CFGVisualizer(Runtime runtime, Scope scope, String name) {
        init(scope);
        this.scriptName = name;
        this.runtime = runtime;
        TEventBus.register(this);
        addMouseWheelListener(e -> {
            if (e.getPreciseWheelRotation() < 0) {
                zoomIn();
            } else {
                zoomOut();
            }
        });
    }

    @_Subscribe
    public void onRuntimeCycleCompleted(CurrentInstructionChanged event) {
        if (!isVisible() || runtime.isDone() || !runtime.getScriptName().equals(scriptName)) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= 50)
        {
            updateGraph(runtime.getRootScope());
            lastUpdateTime = currentTime;
        }
    }
    @_Subscribe
    public void onScriptStateChanged(ScriptStateChanged event) {
        if (!isVisible() || !runtime.getScriptName().equals(scriptName)) {
            return;
        }

        if (!event.getRunning()) {
            updateGraph(runtime.getRootScope());
        }
    }

    private void init(Scope scope)
    {
        this.graph = new mxGraph();
        this.graph.getView().setScale(scale);
        this.graph.setCellsEditable(false);
        this.graph.setAutoOrigin(true);
        this.graph.setAutoSizeCells(true);
        this.graph.setHtmlLabels(true);
        this.graph.setAllowDanglingEdges(true);
        setStyles();
        this.parent = graph.getDefaultParent();
        graph.getModel().beginUpdate();
        try {
            scopeStack.clean();
            linkBacks.clear();
            nodeCounter = 0;
            edgeLabels.clear();
            nodesMap.clear();
            alphabetIterator.reset();
            numericIterator.reset();
            processNode(scope, null);
        } finally {
            graph.getModel().endUpdate();
            applyTreeLayout();
            graph.setCellsMovable(false);
            graph.setCellsResizable(false);
            graph.setCellsEditable(false);
            graph.setEdgeLabelsMovable(false);
            graph.setConnectableEdges(false);
        }

        applyTreeLayout();

        removeAll();
        graph.setEventsEnabled(false);
        mxGraphComponent  graphComponent = new mxGraphComponent(graph);
        graphComponent.addMouseWheelListener(e -> {
            if (e.getPreciseWheelRotation() < 0) {
                zoomIn();
            } else {
                zoomOut();
            }
        });
        add(graphComponent);
        edgeLabels.clear();
        nodesMap.clear();
    }

    public void updateGraph(Scope scope) {
        SwingUtilities.invokeLater(() -> {
            init(scope);
            revalidate();
            repaint();
        });
    }

    public void changeScript(Runtime runtime, String name)
    {
        this.scriptName = name;
        this.runtime = runtime;
    }

    /**
     * Sets the styles for the graph
     */
    private void setStyles() {
        Map<String, Object> edgeStyle = this.graph.getStylesheet().getDefaultEdgeStyle();
        edgeStyle.put(mxConstants.STYLE_ROUNDED, true);
        edgeStyle.put(mxConstants.STYLE_ELBOW, mxConstants.ELBOW_VERTICAL);
        edgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_DIAMOND);
        edgeStyle.put(mxConstants.STYLE_TARGET_PERIMETER_SPACING, 1d);
        edgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.25d);

        Map<String, Object> vertexStyle = this.graph.getStylesheet().getDefaultVertexStyle();
        vertexStyle.put(mxConstants.STYLE_AUTOSIZE, 1);
        vertexStyle.put(mxConstants.STYLE_SPACING, "5");
        vertexStyle.put(mxConstants.STYLE_ORTHOGONAL, "true");
        vertexStyle.put(mxConstants.STYLE_ROUNDED, true);
        vertexStyle.put(mxConstants.STYLE_ARCSIZE, 5);
        vertexStyle.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_LEFT);
        mxGraphics2DCanvas.putShape(mxConstants.SHAPE_RECTANGLE, new mxRectangleShape() {
            @Override
            protected int getArcSize(mxCellState state, double w, double h) {
                return 10;
            }
        });
        mxStylesheet stylesheet = new mxStylesheet();
        stylesheet.setDefaultEdgeStyle(edgeStyle);
        stylesheet.setDefaultVertexStyle(vertexStyle);
        this.graph.setStylesheet(stylesheet);
    }

    /**
     * Processes a node in the AST
     * @param scope The node to process
     * @param parentId The ID of the parent node
     */
    private void processNode(Scope scope, String parentId) {
        String edgeLabel = "";
        int stackNumber = numericIterator.getNextNumber();
        String label = "<html>" + colorize("//Block-" + stackNumber, Colors.NOTATION) + "\n" + createLabelFromScope(scope) + "</html>";
        String nodeHash = HashUtil.getSha256Hash(scope.toJson());
        if(edgeLabels.containsKey(nodeHash))
        {
            edgeLabel = edgeLabels.get(nodeHash);
        }
        String color = Colors.BACKGROUND;
        if(isCurrent)
        {
            color = Colors.BACKGROUND_CURRENT;
            label = label.replace(Colors.BACKGROUND, Colors.BACKGROUND_CURRENT);
        }
        Object graphNode = graph.insertVertex(parent, null, label, 0, 0, 80, 30,"fillColor=" + color + ";fontSize=12;");
        graph.updateCellSize(graphNode); // Resize node to fit content
        nodesMap.put(label, graphNode);

        if (parentId != null) {
            graph.insertEdge(parent, null, edgeLabel, nodesMap.get(parentId), graphNode, "fontSize=12;");
        }

        if(!linkBacks.isEmpty())
        {
            linkBacks.clear();
        }

        boolean isWhileCondition = scope.getConditions() != null && scope.getConditions().getType() != null && scope.getConditions().getType().equals(ConditionType.WHILE);

        scopeStack.push(stackNumber, isWhileCondition, graphNode);
        for (Element element : scope.getElements().values()) {
            if(!element.getType().equals(ElementType.SCOPE))
                continue;
            processNode((Scope) element, label);
        }

        scopeStack.pop();
    }

    /**
     * Creates a label for a scope
     * @param scope The scope scope
     * @return The label
     */
    private String createLabelFromScope(Scope scope)
    {
        this.isCurrent = false;
        String counter = colorize("[" + nodeCounter++ + "]", Colors.BACKGROUND);
        StringBuilder label = new StringBuilder();
        String tab = "";
        String close = "";

        if (scope.getConditions() != null && scope.getConditions().getType() != null )
        {
            Conditions conditions = scope.getConditions();
            boolean current = conditions.isCurrent();
            ConditionType type = conditions.getType();

            if(conditions.getUserFunctionName() != null)
            {
                label.append(colorize("function", current ? Colors.CURRENT : Colors.KEYWORDS)).append(" ")
                     .append(colorize(conditions.getUserFunctionName(), current ? Colors.CURRENT : Colors.FUNCTIONS))
                     .append(colorize("(", current ? Colors.CURRENT : Colors.OPERATORS));
            }
            else
            {
                label.append(colorize(type.name().toLowerCase(), current ? Colors.CURRENT : Colors.KEYWORDS)).append(colorize("(", current ? Colors.CURRENT : Colors.OPERATORS));
            }

            String compString = "";
            for (Map.Entry<Integer, Condition> entry : scope.getConditions().getConditions().entrySet())
            {
                Condition condition = entry.getValue();

                if(condition.getComparator() != null)
                {
                    switch (condition.getComparator()) {
                        case GT:
                            compString = " > ";
                            break;
                        case LT:
                            compString = " < ";
                            break;
                        case GTEQ:
                            compString = " >= ";
                            break;
                        case LTEQ:
                            compString = " <= ";
                            break;
                        case EQ:
                            compString = " == ";
                            break;
                        case NEQ:
                            compString = " != ";
                            break;
                    }
                }
                String left;
                if(condition.getLeft() == null)
                {
                    left = "";
                }
                else if(condition.getLeft() instanceof MethodCall)
                {
                    left = createLabelFromNode((Element) condition.getLeft());
                }
                else if(condition.getLeft() instanceof ArrayAccess)
                {
                    left = processArrayAccess((ArrayAccess) condition.getLeft());
                }
                else
                {
                    String value = isStringArg(condition.getLeft()) ? condition.getLeft() + "\"" : condition.getLeft().toString();
                    left = colorize(value, current ? Colors.CURRENT : Colors.VALUES);
                }
                String right;
                if(condition.getRight() == null)
                {
                    right = "";
                }
                else if(condition.getRight() instanceof MethodCall)
                {
                    right = createLabelFromNode((Element) condition.getRight());
                }
                else if(condition.getRight() instanceof ArrayAccess)
                {
                    right = processArrayAccess((ArrayAccess) condition.getRight());
                }
                else
                {
                    String value = isStringArg(condition.getLeft()) ? condition.getRight() + "\"" : condition.getRight().toString();
                    right = colorize(value, current ? Colors.CURRENT : Colors.VALUES);
                }

                label.append(left)
                        .append(colorize(compString, current ? Colors.CURRENT : Colors.OPERATORS))
                        .append(right);

                if (conditions.getGlues().containsKey(entry.getKey()))
                {
                    switch (conditions.getGlues().get(entry.getKey()))
                    {
                        case AND:
                            label.append(colorize(" && ", current ? Colors.CURRENT : Colors.OPERATORS));
                            break;
                        case OR:
                            label.append(colorize(" || ", current ? Colors.CURRENT : Colors.OPERATORS));
                            break;
                    }
                }
            }
            label.append(colorize(") {", current ? Colors.CURRENT : Colors.OPERATORS));
            close = colorize("\n}", Colors.OPERATORS);
            tab = colorize(". . . ", Colors.BACKGROUND);
        }

        for(Element element : scope.getElements().values())
        {
            if(element.getType().equals(ElementType.SCOPE))
            {
                String newScopeLabel = alphabetIterator.getNextLetter();
                label.append("\n").append(tab).append(colorize("[scope] ", Colors.FUNCTIONS)).append(colorize("//flows to edge " + newScopeLabel, Colors.NOTATION));
                edgeLabels.put(HashUtil.getSha256Hash(((Scope)element).toJson()), "<html>" + colorize(newScopeLabel, Colors.EDGE_LABEL_COLOR) + "</html>");
                continue;
            }
            label.append("\n").append(tab).append(createLabelFromNode(element));
        }

        label.append(close);

        if(scope.getElseElements() != null)
        {
            label.append(colorize("\nelse", Colors.KEYWORDS)).append(colorize(" {", Colors.OPERATORS));
            for(Element element : scope.getElseElements().values())
            {
                if(element.getType().equals(ElementType.SCOPE))
                {
                    String newScopeLabel = alphabetIterator.getNextLetter();
                    label.append("\n").append(tab).append(colorize("[scope] ", Colors.FUNCTIONS)).append(colorize("//flows to edge " + newScopeLabel, Colors.NOTATION));
                    edgeLabels.put(HashUtil.getSha256Hash(((Scope)element).toJson()), "<html>" + colorize(newScopeLabel, Colors.EDGE_LABEL_COLOR) + "</html>");
                    continue;
                }
                label.append("\n").append(tab).append(createLabelFromNode(element));
            }
            label.append(close);
        }
        return cleanLabel(label + counter);
    }

    /**
     * Creates a label for a node
     * @param node The node
     * @return The label
     */
    private String createLabelFromNode(Element node) {
        ElementType nodeType = node.getType();
        boolean current = node.isCurrent();
        String label = "";
        String flowTo = "";

        if (nodeType.equals(ElementType.VARIABLE_ASSIGNMENT)) {
            VariableAssignment variableAssignment = (VariableAssignment) node;
            String varName;
            Object var = variableAssignment.getVar();
            if(var instanceof ArrayAccess) {
                varName = processArrayAccess((ArrayAccess) var);
            } else {
                String name = var == null ? "NULL_HERE_IDK" : var.toString();
                varName = colorize(name, current ? Colors.CURRENT : Colors.VARIABLES);
            }
            Object element = null;
            if (!variableAssignment.getValues().isEmpty()) {
                element = variableAssignment.getValues().get(0);
            }
            String valuesStr = "";

            if (element != null)
            {
                if(element instanceof MethodCall)
                {
                    valuesStr = createLabelFromNode((Element) element);
                }
                else
                {
                    String value = isStringArg(element) ? element + "\"" : element.toString();
                    valuesStr = colorize(value, current ? Colors.CURRENT : Colors.VALUES);
                }
            }

            AssignmentType type = ((VariableAssignment) node).getAssignmentType();
            switch (type)
            {
                case INCREMENT:
                    valuesStr = colorize(" += ", current ? Colors.CURRENT : Colors.OPERATORS) + valuesStr;
                    break;
                case DECREMENT:
                    valuesStr = colorize(" -= ", current ? Colors.CURRENT : Colors.OPERATORS) + valuesStr;
                    break;
                case ADD_ONE:
                    valuesStr = colorize("++", current ? Colors.CURRENT : Colors.OPERATORS) + valuesStr;
                    break;
                case REMOVE_ONE:
                    valuesStr = colorize("--", current ? Colors.CURRENT : Colors.OPERATORS) + valuesStr;
                    break;
                default:
                    valuesStr = colorize(" = ", current ? Colors.CURRENT : Colors.OPERATORS) + valuesStr;
                    break;
            }
            label += varName + valuesStr;
        }
        else if (nodeType.equals(ElementType.FUNCTION_CALL))
        {
            MethodCall methodCall = (MethodCall) node;
            if(methodCall.getName().equals("breakpoint"))
            {
                label += colorize("BREAKPOINT", current ? Colors.CURRENT : "#FF0000");
            }
            else
            {
                String name = colorize(methodCall.getName(), current ? Colors.CURRENT : Colors.FUNCTIONS);
                StringBuilder valuesStr = new StringBuilder();
                for(Object arg : methodCall.getArgs())
                {
                    if(arg instanceof MethodCall)
                    {
                        valuesStr.append(createLabelFromNode((Element) arg))
                                .append(colorize(", ", current ? Colors.CURRENT : Colors.OPERATORS));
                    }
                    if(arg instanceof ArrayAccess)
                    {
                        valuesStr.append(processArrayAccess((ArrayAccess) arg))
                                .append(colorize(", ", current ? Colors.CURRENT : Colors.OPERATORS));
                    }
                    else
                    {
                        String value = isStringArg(arg) ? arg + "\"" : arg.toString();
                        valuesStr.append(colorize(value, current ? Colors.CURRENT : Colors.VALUES))
                                .append(colorize(", ", current ? Colors.CURRENT : Colors.OPERATORS));
                    }
                }
                if (valuesStr.length() > 0) {
                    valuesStr.setLength(valuesStr.length() - colorize(", ", current ? Colors.CURRENT : Colors.OPERATORS).length()); // Remove the last comma and space
                }
                label += name + colorize("(", current ? Colors.CURRENT : Colors.OPERATORS) + valuesStr + colorize(")", current ? Colors.CURRENT : Colors.OPERATORS);

                if (name.contains(">break<")) {
                    int to = scopeStack._break(linkBacks);
                    flowTo = colorize(" //Flows to block-" + to, current ? Colors.CURRENT : Colors.NOTATION);
                } else if (name.contains(">continue<")) {
                    int to = scopeStack._continue(linkBacks);
                    flowTo = colorize(" //Flows to block-" + to, current ? Colors.CURRENT : Colors.NOTATION);
                }
            }
        } else {
            label += " " + nodeType;
        }

        return label + flowTo;
    }

    private String processArrayAccess(ArrayAccess arrayAccess)
    {
        String varName = arrayAccess.getVariable();
        String index = arrayAccess.getIndex() == null ? "" : arrayAccess.getIndex();
        return colorize(varName, Colors.VARIABLES) + colorize("[", Colors.OPERATORS) + colorize(index, Colors.VALUES) + colorize("]", Colors.OPERATORS);
    }

    /**
     * Applies a tree layout to the graph
     */
    private void applyTreeLayout() {
        mxCompactTreeLayout layout = new mxCompactTreeLayout(graph, false);
        layout.setEdgeRouting(true);
        layout.setLevelDistance(20);
        layout.setNodeDistance(10);
        layout.setMoveTree(true);
        layout.execute(parent);
    }

    /**
     * Colorizes a string
     * @param str The string to colorize
     * @param color The color to use
     * @return The colorized string
     */
    private String colorize(String str, String color) {
        str = TextUtil.escapeHtml(str);
        String style = "color: " + color + ";";
        if (color.equals(Colors.CURRENT)) {
            this.isCurrent = true;
            style += " background-color: " + Colors.HIGHLIGHT + ";";
        }
        return "<font style=\"" + style + "\">" + str + "</font>";
    }

    /**
     * Cleans a label
     * @param str The label to clean
     * @return The cleaned label
     */
    public String cleanLabel(String str) {
        if (str.startsWith("\n"))
        {
            return str.substring(1);
        }
        return str.replaceAll("(?m)^[ \t]*\r?\n", "");
    }

    /**
     * Zooms in on the graph
     */
    private void zoomIn() {
        scale += 0.1;
        graph.getView().setScale(scale);
        revalidate();
        repaint();
    }

    /**
     * Zooms out on the graph
     */
    private void zoomOut() {
        scale -= 0.1;
        if (scale < 0.1) {
            scale = 0.1;
        }
        graph.getView().setScale(scale);
        revalidate();
        repaint();
    }

    private boolean isStringArg(Object arg) {
        if(arg instanceof String)
        {
            String str = (String) arg;
            if(str.startsWith("\""))
            {
                return true;
            }
        }
        return false;
    }
}