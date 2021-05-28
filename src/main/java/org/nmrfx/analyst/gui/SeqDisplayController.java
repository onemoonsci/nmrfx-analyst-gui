package org.nmrfx.analyst.gui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.PropertySheet;
import org.nmrfx.chart.Symbol;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsContextProxy;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.graphicsio.PDFGraphicsContext;
import org.nmrfx.graphicsio.SVGGraphicsContext;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.predict.ResidueProperties;
import org.nmrfx.utils.GUIUtils;
import org.nmrfx.utils.properties.BooleanOperationItem;
import org.nmrfx.utils.properties.CheckComboOperationItem;
import org.nmrfx.utils.properties.ChoiceOperationItem;
import org.nmrfx.utils.properties.DoubleRangeOperationItem;
import org.nmrfx.utils.properties.NvFxPropertyEditorFactory;

/**
 * FXML Controller class
 *
 * @author brucejohnson
 */
public class SeqDisplayController implements Initializable {

    private final static Map<String, Double> SCALES = new HashMap<>();
    Color[] colors = {Color.BLUE, Color.RED, Color.BLACK, Color.GREEN, Color.CYAN, Color.MAGENTA, Color.YELLOW};

    static {
        SCALES.put("N", -0.472);
        SCALES.put("C", 0.185);
        SCALES.put("CB", -0.154);
        SCALES.put("CA", 0.198);
        SCALES.put("H", -0.067);
        SCALES.put("HA", -0.026);
        SCALES.put("HB", 0.022);

    }

    static final String[] RNA_ATOMS = {"H5,H8", "H6,H2", "H1'", "H2'", "H3'", "H4'", "H5'",
        "C5,C8", "C6,C2", "C1'", "C2'", "C3'", "C4'", "C5'"
    };

    static final String[] PROTEIN_ATOMS = {"H", "N", "HA", "C", "CA", "CB"};
    Stage stage = null;
    @FXML
    MasterDetailPane masterDetailPane;
    @FXML
    BorderPane attrBorderPane;
    @FXML
    BorderPane atomsPane;
    @FXML
    Slider nResiduesSlider;
    @FXML
    Label nResiduesLabel;
    @FXML
    Slider fontSizeSlider;
    @FXML
    Label fontSizeLabel;
    @FXML
    Label scaleLabel;
    @FXML
    CheckBox detailsCheckBox;

    PropertySheet propertySheet;

    ScrollPane canvasPane;
    Canvas seqCanvas;
    SimpleIntegerProperty nResProp = new SimpleIntegerProperty();
    SimpleIntegerProperty fontSizeProp = new SimpleIntegerProperty();

    ChoiceOperationItem showResNumberItem;
    BooleanOperationItem showAtomShiftsItem;
    BooleanOperationItem showViennaItem;
    BooleanOperationItem showSeqCharItem;
    BooleanOperationItem showZIRDItem;
    ChoiceOperationItem modeZIRDItem;
    BooleanOperationItem showAtomShiftsDotItem;
    BooleanOperationItem showAtomShiftsCombineItem;
    CheckComboOperationItem proteinShiftsAtomsItem;
    CheckComboOperationItem rnaShiftsAtomsItem;
    CheckComboOperationItem groupShiftsAtomsItem;
    DoubleRangeOperationItem atomScaleItem;
    DoubleRangeOperationItem zirdHeightItem;

    double smallGap = 5.0;
    boolean verticalResNums = false;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        canvasPane = new ScrollPane();
        seqCanvas = new Canvas();
        canvasPane.setContent(seqCanvas);
        masterDetailPane.setMasterNode(canvasPane);
        propertySheet = new PropertySheet();
        masterDetailPane.setDetailSide(Side.RIGHT);

        masterDetailPane.setDetailNode(propertySheet);
        masterDetailPane.setShowDetailNode(true);
        detailsCheckBox.setSelected(true);
        attrBorderPane.setCenter(masterDetailPane);
        seqCanvas.setWidth(1000.0);
        seqCanvas.setHeight(1000.0);
        nResiduesSlider.setMin(20);
        nResiduesSlider.setMax(200);
        nResiduesSlider.valueProperty().bindBidirectional(nResProp);
        nResiduesSlider.setValue(100);
        nResProp.addListener(e -> {
            nResiduesLabel.setText(String.valueOf(nResProp.get()));
            refresh();
        });
        fontSizeSlider.setMin(5);
        fontSizeSlider.setMax(30);
        fontSizeSlider.valueProperty().bindBidirectional(fontSizeProp);
        fontSizeSlider.setValue(12);
        fontSizeProp.addListener(e -> {
            fontSizeLabel.setText(String.valueOf(fontSizeProp.get()));
            refresh();
        });
        masterDetailPane.showDetailNodeProperty().bindBidirectional(detailsCheckBox.selectedProperty());

        propertySheet.setPropertyEditorFactory(new NvFxPropertyEditorFactory());
        propertySheet.setMode(PropertySheet.Mode.CATEGORY);
        propertySheet.setModeSwitcherVisible(false);
        propertySheet.setSearchBoxVisible(false);

        showResNumberItem = new ChoiceOperationItem((a, b, c) -> {
            refresh();
        }, "Horizontal", List.of("Horizontal", "Vertical", "Off"), "Annotations", "Residue Orient", "Orientation for residue numbers");

        showSeqCharItem = new BooleanOperationItem((a, b, c) -> {
            refresh();
        }, true, "Annotations", "Residue Char", "Show One-Letter Residue Character");
        showViennaItem = new BooleanOperationItem((a, b, c) -> {
            refresh();
        }, Boolean.FALSE, "Annotations", "Dot-Bracket", "Show RNA Dot-Bracket (Vienna)");

        showZIRDItem = new BooleanOperationItem((a, b, c) -> {
            refresh();
        }, Boolean.FALSE, "Residue Order Value", "Display", "Display IRD");

        List<String> zirdModeChoices = List.of("Dot", "Bar");
        modeZIRDItem = new ChoiceOperationItem((a, b, c) -> {
            refresh();
        }, "Dot", zirdModeChoices, "Residue Order Value", "Mode", "Display Mode for IRD");

        showAtomShiftsItem = new BooleanOperationItem((a, b, c) -> {
            refresh();
        }, Boolean.FALSE, "Atom Shifts", "Display", "Display Atom Shift Deviation to Ref");

        showAtomShiftsDotItem = new BooleanOperationItem((a, b, c) -> {
            refresh();
        }, Boolean.FALSE, "Atom Shifts", "Dot", "Show delta values as dot symbols");

        showAtomShiftsCombineItem = new BooleanOperationItem((a, b, c) -> {
            refresh();
        }, Boolean.FALSE, "Atom Shifts", "Combine", "Combine multiple atoms per line");

        List<String> proteinShiftsAtoms = List.of("H", "N", "C", "CA", "CB", "HA", "HB");
        proteinShiftsAtomsItem = new CheckComboOperationItem((a) -> {
            refresh();
        }, "H", proteinShiftsAtoms, "Atom Shifts", "Protein Atoms", "Select atoms for display");

        List<String> rnaShiftsAtoms = List.of("H5,H8", "H5", "H8", "H6,H2", "H6", "H2", "H1'", "H2'", "H3'", "H4'", "H5'",
                "C5,C8", "C5", "C8", "C6,C2", "C6", "C2", "C1'", "C2'", "C3'", "C4'", "C5'");
        rnaShiftsAtomsItem = new CheckComboOperationItem((a) -> {
            refresh();
        }, "H", rnaShiftsAtoms, "Atom Shifts", "RNA Atoms", "Select atoms for display");

        List<String> groupShiftsAtoms = List.of("C", "ribose");
        groupShiftsAtomsItem = new CheckComboOperationItem((a) -> {
            refresh();
        }, "", groupShiftsAtoms, "Atom Shifts", "Group By", "Select types to group atoms with");

        atomScaleItem = new DoubleRangeOperationItem((a, b, c) -> refresh(),
                5.0, 1.0, 20.0, false, "Atom Shifts", "Scale", "Scale delta values by by this amount");

        zirdHeightItem = new DoubleRangeOperationItem((a, b, c) -> refresh(),
                5.0, 1.0, 20.0, false, "Residue Order Value", "Height", "Scale region height by this amount");

        propertySheet.getItems().addAll(showResNumberItem, showSeqCharItem, showViennaItem,
                showAtomShiftsItem,
                proteinShiftsAtomsItem, rnaShiftsAtomsItem,
                showAtomShiftsDotItem,
                showAtomShiftsCombineItem, groupShiftsAtomsItem, atomScaleItem,
                showZIRDItem, modeZIRDItem, zirdHeightItem);
        masterDetailPane.setDividerPosition(0.7);
        refresh();

    }

    public static SeqDisplayController create() {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/SeqDisplayScene.fxml"));
        SeqDisplayController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((BorderPane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<SeqDisplayController>getController();
            controller.stage = stage;
            stage.setTitle("Sequence Display");
            stage.setScene(scene);
            stage.show();
            stage.toFront();

        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }
        return controller;
    }

    public Stage getStage() {
        return stage;
    }

    public List<List<String>> getValidAtoms(Polymer polymer) {
        List<String> testNames = new ArrayList<>();
        if (polymer.isPeptide()) {
            testNames.addAll(proteinShiftsAtomsItem.getValues());
        } else if (polymer.isRNA()) {
            testNames.addAll(rnaShiftsAtomsItem.getValues());
        }
        List<String> groupNames = groupShiftsAtomsItem.getValues();
        List<List<String>> useNames = new ArrayList<>();

        for (String aName : testNames) {
            for (Residue residue : polymer.getResidues()) {
                String[] splitAtoms = aName.split(",");
                boolean gotAtom = false;
                for (String sName : splitAtoms) {
                    Atom atom = residue.getAtom(sName);
                    if (atom != null) {
                        if (atom.getPPM() != null) {
                            int groupNum = 0;
                            int groupCount = 0;
                            for (String groupAtom : groupNames) {
                                int present = 0;
                                if (groupAtom.equals("C")) {
                                    present = aName.startsWith("C") ? 1 : 0;
                                } else if (groupAtom.equals("ribose")) {
                                    present = aName.endsWith("'") ? 1 : 0;
                                }
                                groupNum += (int) Math.pow(2, groupCount) * present;
                                groupCount++;
                            }
                            if (useNames.size() <= groupNum) {
                                for (int i = useNames.size(); i < (groupNum + 1); i++) {
                                    useNames.add(new ArrayList<>());
                                }
                            }
                            useNames.get(groupNum).add(aName);
                            gotAtom = true;
                            break;
                        }
                    }
                }
                if (gotAtom) {
                    break;
                }
            }
        }
        return useNames;
    }

    void drawAtomLabels(GraphicsContextInterface gC, List<String> aNames, double x, double y, double atomBarWidth, double height, boolean symbols) {
        gC.setTextAlign(TextAlignment.RIGHT);
        gC.setTextBaseline(VPos.CENTER);
        y = y + height / 2.0 + smallGap;
        int iAtom = 0;
        double symSize = 0.4 * atomBarWidth;
        for (String aName : aNames) {
            if (symbols) {
                Color color = colors[iAtom % colors.length];
                gC.setFill(color);
                gC.setStroke(color);
                double x1 = x - symSize - 5;
                gC.strokeOval(x1, y - symSize / 2, symSize, symSize);
                gC.fillOval(x1, y - symSize / 2, symSize, symSize);
                gC.setFill(Color.BLACK);
                gC.fillText(aName, x1 - 5, y);
            } else {
                gC.setFill(Color.BLACK);
                gC.fillText(aName, x - 5, y);
            }

            y += height + smallGap;
            iAtom++;
        }
    }

    double getScale(Atom atom) {
        double scale = atom.getRefPPM(0).getError();
        return scale;

    }

    Optional<Double> getDelta(Atom atom) {
        Optional<Double> result = Optional.empty();
        if (atom != null) {
            Double ppm = atom.getPPM();
            Double rppm = atom.getRefPPM();
            if ((ppm != null) && (rppm != null)) {
                double scale = getScale(atom);
                scale *= atomScaleItem.getValue();
                double delta = (ppm - rppm) / scale;
                result = Optional.of(delta);
            }
        }
        return result;
    }

    void drawAtomScores(GraphicsContextInterface gC, Residue residue,
            List<String> aNames, double x, double y, double atomBarWidth, double height) {
        y = y + height / 2.0 + smallGap;
        for (String aName : aNames) {
            String[] splitAtoms = aName.split(",");
            for (String sName : splitAtoms) {
                Atom atom = residue.getAtom(sName);
                Optional<Double> deltaOpt = getDelta(atom);
                if (deltaOpt.isPresent()) {
                    double delta = deltaOpt.get();
                    if (delta > 1.0) {
                        delta = 1.0;
                    } else if (delta < -1.0) {
                        delta = -1.0;
                    }
                    double width = atomBarWidth - 2;
                    double halfWidth = width / 2.0;
                    double x1 = x - halfWidth;
                    double w = width;

                    double h = Math.abs(delta * height / 2.0);
                    double y1;

                    if (delta < 0.0) {
                        y1 = y;
                        gC.setFill(Color.RED);
                    } else {
                        y1 = y - h;
                        gC.setFill(Color.BLUE);
                    }
                    gC.fillRect(x1, y1, w, h);
                }
            }
            y += height + smallGap;
        }
        gC.setFill(Color.BLACK);
    }

    void drawDotScores(GraphicsContextInterface gC, Residue residue,
            List<String> aNames, double x, double y,
            double atomBarWidth, double height, boolean combineMode) {
        y = y + height / 2.0 + smallGap;
        int iAtom = 0;
        double deltaMax = 1.05;
        int nUp = 0;
        int nDown = 0;
        int nGood = 0;
        for (String aName : aNames) {
            String[] splitAtoms = aName.split(",");
            for (String sName : splitAtoms) {
                Atom atom = residue.getAtom(sName);
                if (atom != null) {
                    Double ppm = atom.getPPM();
                    Double rppm = atom.getRefPPM();
                    gC.setStroke(Color.DARKGRAY);
                    gC.strokeLine(x, y - height / 2.0, x, y + height / 2.0);
                    Optional<Double> deltaOpt = getDelta(atom);
                    if (deltaOpt.isPresent()) {
                        double delta = deltaOpt.get();
                        Color color;
                        Symbol symbol = Symbol.CIRCLE;
                        int iOffset;
                        if (delta > 1.0) {
                            iOffset = nUp;
                            if (combineMode) {
                                delta = 1.0 + (deltaMax - 1.0) * (nUp + 1) / 5;
                            } else {
                                delta = deltaMax;
                            }
                            nUp++;
                            if (delta > deltaMax) {
                                delta = deltaMax;
                            }
                            color = Color.RED;
                            symbol = Symbol.TRIANGLE_DOWN;
                        } else if (delta < -1.0) {
                            iOffset = nDown;
                            if (combineMode) {
                                delta = -1.0 - (deltaMax - 1.0) * (nDown + 1) / 5;
                            } else {
                                delta = -deltaMax;
                            }
                            nDown++;
                            if (delta < -deltaMax) {
                                delta = -deltaMax;
                            }
                            color = Color.RED;
                            symbol = Symbol.TRIANGLE_UP;
                        } else {
                            iOffset = nGood;
                            color = Color.BLUE;
                            nGood++;
                        }
                        if (combineMode) {
                            color = colors[iAtom % colors.length];
                        }

                        double width = 0.4 * atomBarWidth;
                        double halfSize = width / 2.0;
                        double x1;
                        if (combineMode) {
                            x1 = x - halfSize / 2.0 + (iOffset % 2) * halfSize;
                        } else {
                            x1 = x;
                        }
                        double w = width;

                        double h = Math.abs(delta * height / deltaMax * 0.5);
                        double y1;
                        if (delta < 0.0) {
                            y1 = y - h;
                        } else {
                            y1 = y + h;
                        }
                        symbol.draw(gC, x1, y1, halfSize, color, color);
                    }
                }
            }
            iAtom++;
            if (!combineMode) {
                y += height + smallGap;
            }
        }
        gC.setFill(Color.BLACK);
        gC.setStroke(Color.BLACK);
    }

    void drawSymbol(GraphicsContextInterface gC, Residue residue,
            double x, double y,
            double atomBarWidth, double height,
            double value, double lower, double upper) {
        y = y + height + smallGap;

        double delta = (value - lower) / (upper - lower);
        if (delta > 1.0) {
            delta = 1.0;
        } else if (delta < 0.0) {
            delta = 0.0;
        }
        double y1 = y - delta * height;
        double radius = atomBarWidth * 0.2;
        Symbol.CIRCLE.draw(gC, x, y1, radius, Color.BLACK, Color.BLACK);

    }

    void drawBar(GraphicsContextInterface gC, Residue residue,
            double x, double y,
            double atomBarWidth, double height,
            double value, double lower, double upper) {

        y = y + height + smallGap;
        double x1 = x - atomBarWidth / 2.0;
        double delta = (value - lower) / (upper - lower);
        if (delta > 1.0) {
            delta = 1.0;
        } else if (delta < 0.0) {
            delta = 0.0;
        }
        double y1 = y - delta * height;
        gC.fillRect(x1, y1, atomBarWidth, y - y1);
    }

    void drawResNumLabel(GraphicsContextInterface gC, double x, double y, int resNum) {
        double fontHeight = gC.getFont().getSize();
        gC.setFill(Color.BLACK);
        gC.setTextBaseline(VPos.BASELINE);
        gC.setTextAlign(TextAlignment.CENTER);
        String resNumStr = String.valueOf(resNum);
        if (verticalResNums) {
            int len = resNumStr.length();
            for (int iNum = 0; iNum < len; iNum++) {
                gC.fillText(resNumStr.substring(iNum, iNum + 1), x, y - fontHeight * (len - iNum - 1) - 5);
            }
        } else {
            gC.fillText(resNumStr, x, y);

        }
    }

    void drawSeqCharLabel(GraphicsContextInterface gC, double x, double y, Residue residue) {
        String text = String.valueOf(residue.getOneLetter());
        gC.setFill(Color.BLACK);
        gC.setTextBaseline(VPos.BASELINE);
        gC.setTextAlign(TextAlignment.CENTER);
        gC.fillText(text, x, y);

    }

    void drawLabel(GraphicsContextInterface gC, double x, double y, String text) {
        gC.setFill(Color.BLACK);
        gC.setTextBaseline(VPos.BASELINE);
        gC.setTextAlign(TextAlignment.CENTER);
        gC.fillText(text, x, y);

    }

    void drawBorder(GraphicsContextInterface gC, double x, double y, double w, double h) {
        gC.setStroke(Color.BLACK);
        gC.strokeRect(x, y, w, h);
    }

    void drawLine(GraphicsContextInterface gC, double x1, double y1, double x2, double y2) {
        gC.setStroke(Color.BLACK);
        gC.strokeLine(x1, y1, x2, y2);
    }

    public void refresh() {
        var gC2D = seqCanvas.getGraphicsContext2D();
        GraphicsContextInterface gC = new GraphicsContextProxy(gC2D);
        double[] canvasSize = drawCanvas(gC, CANVAS_MODE.SIZE);
        seqCanvas.setWidth(canvasSize[0]);
        seqCanvas.setHeight(canvasSize[1]);
        drawCanvas(gC, CANVAS_MODE.DRAW);
    }

    enum CANVAS_MODE {
        DRAW,
        SIZE,
        PICK;
    };

    double[] drawCanvas(GraphicsContextInterface gC, CANVAS_MODE cMode) {

        double cWidth = seqCanvas.getWidth();
        double cHeight = seqCanvas.getHeight();
        gC.setFill(Color.WHITE);
        gC.fillRect(0, 0, cWidth, cHeight);
        gC.setFill(Color.BLACK);
        Molecule mol = (Molecule) MoleculeFactory.getActive();
        if (mol == null) {
            double[] canvasSize = {100, 100};
            return canvasSize;
        }
        gC.setFont(Font.font(fontSizeProp.get()));
        double fontHeight = gC.getFont().getSize();
        double fontWidth = GUIUtils.getTextWidth("M", gC.getFont());
        double heightMultiplier = 1.0;
        double atomBarWidth = fontWidth + 2;
        double atomBarHeight = fontHeight * heightMultiplier + 10;
        double xOrigin = 15 + 6 * fontWidth;
        double sectionGap = fontHeight * 1.0;
        double yOrigin = sectionGap;
        double y = yOrigin;
        double curY = y;
        String dotBracket = mol.getDotBracket();
        int resIndex = 0;
        boolean dotMode = showAtomShiftsDotItem.getValue();
        boolean combineMode = showAtomShiftsCombineItem.getValue();
        double maxX = xOrigin;
        double maxY = yOrigin;
        verticalResNums = showResNumberItem.get().equals("Vertical");
        for (Polymer polymer : mol.getPolymers()) {
            Residue firstRes = polymer.getFirstResidue();
            int firstResNum = firstRes.getResNum();
            int iRes = 0;
            var aNames = getValidAtoms(polymer);
            int biggestGroup = 1;
            for (var group : aNames) {
                biggestGroup = Math.max(biggestGroup, group.size());
            }
            curY = y;
            int nResidues = polymer.getResidues().size();
            int remaining = nResidues - resIndex;
            int residuesInRow = remaining > nResProp.get() ? nResProp.get() : remaining;
            double resWidth = residuesInRow * atomBarWidth;
            maxX = Math.max(maxX, xOrigin + resWidth);
            boolean lastResidueOnLine = false;
            double resNumHeight;
            if (verticalResNums) {
                int nChars = String.valueOf(polymer.getResidues().get(nResidues - 1).getResNum()).length();
                resNumHeight = nChars * fontHeight + 5;
            } else {
                resNumHeight = fontHeight + 5;
            }

            for (Residue residue : polymer.getResidues()) {
                lastResidueOnLine = ((iRes + 1) >= nResProp.get()) || (residue == polymer.getLastResidue());
                double x = iRes * atomBarWidth + atomBarWidth / 2.0 + xOrigin;

                int resNum = residue.getResNum();
                if (!showResNumberItem.get().equals("Off")) {
                    y += resNumHeight;
                    boolean showExtra = false;
                    if (verticalResNums) {
                        if (residue.getPrevious() != null) {
                            if (resNum != (residue.getPrevious().getResNum() + 1)) {
                                showExtra = true;

                            }
                        }
                    }
                    if (showExtra || ((resNum % 10) == 0)) {
                        drawResNumLabel(gC, x, y, resNum);
                    }
                }

                if (showSeqCharItem.getValue()) {
                    y += fontHeight + smallGap;
                    drawSeqCharLabel(gC, x, y, residue);
                }
                if (polymer.isRNA() && showViennaItem.getValue() && (dotBracket != null)) {
                    y += fontHeight + smallGap;
                    if (cMode == CANVAS_MODE.DRAW) {
                        drawLabel(gC, x, y, dotBracket.substring(resIndex, resIndex + 1));
                    }
                }
                if (showAtomShiftsItem.getValue()) {
                    y += smallGap;
                    if (!dotMode) {
                        for (var group : aNames) {
                            if (!group.isEmpty()) {
                                if (cMode == CANVAS_MODE.DRAW) {
                                    if (lastResidueOnLine) {
                                        drawAtomLabels(gC, group, xOrigin, y, atomBarWidth, atomBarHeight, false);
                                    }
                                    drawAtomScores(gC, residue, group, x, y, atomBarWidth, atomBarHeight);
                                }
                                y += (atomBarHeight + smallGap) * group.size();
                            }
                        }
                    } else {
                        for (var group : aNames) {
                            double useHeight = combineMode ? (atomBarHeight + smallGap) * biggestGroup : atomBarHeight;
                            if (cMode == CANVAS_MODE.DRAW) {
                                if (lastResidueOnLine) {
                                    drawAtomLabels(gC, group, xOrigin, y, atomBarWidth, atomBarHeight, combineMode);
                                }

                                drawDotScores(gC, residue, group, x, y, atomBarWidth, useHeight, combineMode);
                            }
                            if (combineMode) {
                                y += useHeight + sectionGap;
                            } else {
                                y += (useHeight + smallGap) * group.size();
                            }
                        }
                    }
                }
                if (showZIRDItem.getValue() && polymer.isPeptide()) {
                    double zIDR = ResidueProperties.calcZIDR(residue, 0, 0);
                    double zIDRHeight = atomBarHeight * zirdHeightItem.doubleValue();
                    double maxZ = 15.0;
                    double minZ = -3.0;
                    if (cMode == CANVAS_MODE.DRAW) {
                        if (modeZIRDItem.getValue().equals("Bar")) {
                            gC.setFill(Color.GRAY);
                            drawBar(gC, residue, x, y, atomBarWidth, zIDRHeight, zIDR, minZ, maxZ);
                        } else {
                            drawSymbol(gC, residue, x, y, atomBarWidth, zIDRHeight, zIDR, minZ, maxZ);
                        }
                        if (lastResidueOnLine) {
                            drawBorder(gC, xOrigin, y + smallGap, resWidth, zIDRHeight);
                            double y1 = y + smallGap + zIDRHeight * (1.0 - ((3.0 - minZ) / (maxZ - minZ)));
                            gC.setStroke(Color.BLUE);
                            drawLine(gC, xOrigin, y1, xOrigin + resWidth, y1);

                        }
                    }
                    y += zIDRHeight;
                }
                maxY = Math.max(maxY, y);
                iRes++;
                if ((iRes >= nResProp.get()) || (residue == polymer.getLastResidue())) {
                    iRes = 0;
                    y = y + sectionGap;
                    curY = y;
                } else {
                    y = curY;
                }
                resIndex++;
            }
        }
        double[] canvasSize = {maxX + 20, maxY + 20};
        return canvasSize;

    }

    @FXML
    public void exportPDFAction(ActionEvent event
    ) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.setInitialDirectory(FXMLController.getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            exportPDF(selectedFile);
        }
    }

    public void exportPDF(File file) {
        exportPDF(file.toString());
    }

    public void exportPDF(String fileName) {
        if (fileName != null) {
            try {
                PDFGraphicsContext pdfGC = new PDFGraphicsContext();
                pdfGC.create(true, seqCanvas.getWidth(), seqCanvas.getHeight(), fileName);
                drawCanvas(pdfGC, CANVAS_MODE.DRAW);
                pdfGC.saveFile();
            } catch (GraphicsIOException ex) {
                Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        stage.setResizable(true);
    }

    @FXML
    public void exportSVGAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to SVG");
        fileChooser.setInitialDirectory(FXMLController.getInitialDirectory());
        File selectedFile = fileChooser.showSaveDialog(null);
        if (selectedFile != null) {
            exportSVG(selectedFile);
        }
    }

    public void exportSVG(File file) {
        exportSVG(file.toString());
    }

    public void exportSVG(String fileName) {
        if (fileName != null) {
            SVGGraphicsContext svgGC = new SVGGraphicsContext();
            svgGC.create(true, seqCanvas.getWidth(), seqCanvas.getHeight(), fileName);
            drawCanvas(svgGC, CANVAS_MODE.DRAW);
            svgGC.saveFile();
        }
        stage.setResizable(true);
    }

}