package de.saring.sportstracker.gui.dialogs;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.util.converter.NumberStringConverter;

import javax.inject.Inject;
import javax.inject.Provider;

import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.Validator;

import de.saring.exerciseviewer.data.EVExercise;
import de.saring.exerciseviewer.gui.EVMain;
import de.saring.exerciseviewer.parser.ExerciseParser;
import de.saring.exerciseviewer.parser.ExerciseParserFactory;
import de.saring.sportstracker.data.Equipment;
import de.saring.sportstracker.data.Exercise;
import de.saring.sportstracker.data.Exercise.IntensityType;
import de.saring.sportstracker.data.SportSubType;
import de.saring.sportstracker.data.SportType;
import de.saring.sportstracker.gui.STContext;
import de.saring.sportstracker.gui.STDocument;
import de.saring.util.StringUtils;
import de.saring.util.ValidationUtils;
import de.saring.util.gui.javafx.NameableStringConverter;
import de.saring.util.gui.javafx.SpeedToStringConverter;
import de.saring.util.gui.javafx.TimeInSecondsToStringConverter;
import de.saring.util.unitcalc.ConvertUtils;
import de.saring.util.unitcalc.FormatUtils;

/**
 * Controller (MVC) class of the Wieght dialog for editing / adding Exercise entries.
 *
 * @author Stefan Saring
 */
public class ExerciseDialogController extends AbstractDialogController {

    private static final Logger LOGGER = Logger.getLogger(ExerciseDialogController.class.getName());

    private final STDocument document;

    @Inject
    private Provider<HRMFileOpenDialog> prHRMFileOpenDialog;

    @Inject
    private Provider<EVMain> prExerciseViewer;

    @FXML
    private DatePicker dpDate;

    // TODO use formatted TextField, will be introduced in JavaFX 8u40
    @FXML
    private TextField tfHour;

    // TODO use formatted TextField, will be introduced in JavaFX 8u40
    @FXML
    private TextField tfMinute;

    @FXML
    private ChoiceBox<SportType> cbSportType;

    @FXML
    private ChoiceBox<SportSubType> cbSportSubtype;

    @FXML
    private ChoiceBox<IntensityType> cbIntensity;

    @FXML
    private ChoiceBox<Equipment> cbEquipment;

    @FXML
    private TextField tfDistance;

    @FXML
    private TextField tfAvgSpeed;

    @FXML
    private TextField tfDuration;

    @FXML
    private RadioButton rbAutoCalcDistance;

    @FXML
    private RadioButton rbAutoCalcAvgSpeed;

    @FXML
    private RadioButton rbAutoCalcDuration;

    @FXML
    private TextField tfAscent;

    @FXML
    private TextField tfAvgHeartrate;

    @FXML
    private TextField tfCalories;

    @FXML
    private TextField tfHrmFile;

    @FXML
    private TextArea taComment;

    @FXML
    private Label laDistance;

    @FXML
    private Label laAvgSpeed;

    @FXML
    private Label laAscent;

    @FXML
    private Button btViewHrmFile;

    @FXML
    private Button btImportHrmFile;

    /** ViewModel of the edited Exercise. */
    private ExerciseViewModel exerciseViewModel;

    /** Flag whether the HRM file specified in the passed exercise needs to be imported when starting the dialog. */
    private boolean importHrmFileOnStart = false;

    /** Equipment for selection "none", same for all sport types. */
    private final Equipment equipmentNone;

    /**
     * Index of the previous similar exercise in the exercise list from which
     * the last comment has been copied. It's null when it was not used yet.
     */
    private Integer previousExerciseIndex = null;


    /**
     * Standard c'tor for dependency injection.
     *
     * @param context the SportsTracker UI context
     * @param document the SportsTracker model/document
     */
    @Inject
    public ExerciseDialogController(final STContext context, final STDocument document) {
        super(context);
        this.document = document;

        equipmentNone = new Equipment(Integer.MAX_VALUE);
        equipmentNone.setName(context.getFxResources().getString("st.dlg.exercise.equipment.none.text"));
    }

    /**
     * Displays the Exercise dialog for the passed Exercise instance.
     *
     * @param parent parent window of the dialog
     * @param exercise Exercise to be edited
     * @param importHrmFileOnStart flag for importing the HRM file specified in the exercise
     *                             (e.g. when dropping exercise files to main window)
     */
    public void show(final Window parent, final Exercise exercise, boolean importHrmFileOnStart) {
        this.exerciseViewModel = new ExerciseViewModel(exercise, document.getOptions().getUnitSystem());
        this.importHrmFileOnStart = importHrmFileOnStart;

        // if no equipment is specified for exercise => use dummy equipment "none"
        if (exerciseViewModel.equipment.get() == null) {
            exerciseViewModel.equipment.set(equipmentNone);
        }

        final boolean newExercise = document.getExerciseList().getByID(exercise.getId()) == null;
        final String dlgTitleKey = newExercise ? "st.dlg.exercise.title.add" : "st.dlg.exercise.title";
        final String dlgTitle = context.getFxResources().getString(dlgTitleKey);

        showEditDialog("/fxml/ExerciseDialog.fxml", parent, dlgTitle);
    }

    @Override
    protected void setupDialogControls() {

        // insert unit names in input labels with placeholders
        laDistance.setText(String.format(laDistance.getText(), context.getFormatUtils().getDistanceUnitName()));
        laAvgSpeed.setText(String.format(laAvgSpeed.getText(), context.getFormatUtils().getSpeedUnitName()));
        laAscent.setText(String.format(laAscent.getText(), context.getFormatUtils().getAltitudeUnitName()));

        setupChoiceBoxes();
        fillSportTypeDependentChoiceBoxes();

        setupBinding();
        setupValidation();
        setupAutoCalculation();

        // enable View and Import HRM buttons only when an HRM file is specified
        btViewHrmFile.disableProperty().bind(Bindings.isEmpty(tfHrmFile.textProperty()));
        btImportHrmFile.disableProperty().bind(Bindings.isEmpty(tfHrmFile.textProperty()));

        // don't display value '0' for optional inputs when no data available
        if (exerciseViewModel.ascent.get() == 0) {
            tfAscent.setText("");
        }
        if (exerciseViewModel.avgHeartRate.get() == 0) {
            tfAvgHeartrate.setText("");
        }
        if (exerciseViewModel.calories.get() == 0) {
            tfCalories.setText("");
        }

        // import HRM file when requested at dialog startup
        if (importHrmFileOnStart && StringUtils.getTrimmedTextOrNull(exerciseViewModel.hrmFile.get()) != null) {
            Platform.runLater(() -> onImportHrmFile(null));
        }
    }

    @Override
    protected boolean validateAndStore() {
        final Exercise newExercise = exerciseViewModel.getExercise();

        // if selected equipment is "none" -> remove this dummy selection
        if (equipmentNone.equals(newExercise.getEquipment())) {
            newExercise.setEquipment(null);
        }

        // store the new Exercise, no further validation needed
        document.getExerciseList().set(newExercise);
        return true;
    }

    /**
     * Setup of the binding between view model and the UI controls.
     */
    private void setupBinding() {

        dpDate.valueProperty().bindBidirectional(exerciseViewModel.date);
        tfHour.textProperty().bindBidirectional(exerciseViewModel.hour, new NumberStringConverter("00"));
        tfMinute.textProperty().bindBidirectional(exerciseViewModel.minute, new NumberStringConverter("00"));
        cbSportType.valueProperty().bindBidirectional(exerciseViewModel.sportType);
        cbSportSubtype.valueProperty().bindBidirectional(exerciseViewModel.sportSubType);
        cbIntensity.valueProperty().bindBidirectional(exerciseViewModel.intensity);
        tfDistance.textProperty().bindBidirectional(exerciseViewModel.distance, new NumberStringConverter());
        tfAvgSpeed.textProperty().bindBidirectional(exerciseViewModel.avgSpeed,
                new SpeedToStringConverter(context.getFormatUtils()));
        tfDuration.textProperty().bindBidirectional(exerciseViewModel.duration,
                new TimeInSecondsToStringConverter(context.getFormatUtils()));

        tfAscent.textProperty().bindBidirectional(exerciseViewModel.ascent, new NumberStringConverter());
        tfAvgHeartrate.textProperty().bindBidirectional(exerciseViewModel.avgHeartRate, new NumberStringConverter());
        tfCalories.textProperty().bindBidirectional(exerciseViewModel.calories, new NumberStringConverter());
        cbEquipment.valueProperty().bindBidirectional(exerciseViewModel.equipment);
        tfHrmFile.textProperty().bindBidirectional(exerciseViewModel.hrmFile);
        taComment.textProperty().bindBidirectional(exerciseViewModel.comment);
    }

    /**
     * Setup of the validation of the UI controls.
     */
    private void setupValidation() {

        validationSupport.registerValidator(dpDate,
                Validator.createEmptyValidator(context.getFxResources().getString("st.dlg.exercise.error.date")));
        validationSupport.registerValidator(tfHour, true, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfHour, context.getFxResources().getString("st.dlg.exercise.error.time"),
                        !ValidationUtils.isValueIntegerBetween(newValue, 0, 23)));
        validationSupport.registerValidator(tfMinute, true, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfMinute, context.getFxResources().getString("st.dlg.exercise.error.time"),
                        !ValidationUtils.isValueIntegerBetween(newValue, 0, 59)));
        validationSupport.registerValidator(cbSportType,
                Validator.createEmptyValidator(context.getFxResources().getString("st.dlg.exercise.error.no_sport_type")));
        validationSupport.registerValidator(cbSportSubtype,
                Validator.createEmptyValidator(context.getFxResources().getString("st.dlg.exercise.error.no_sport_subtype")));
        validationSupport.registerValidator(cbIntensity,
                Validator.createEmptyValidator(context.getFxResources().getString("st.dlg.exercise.error.no_intensity")));

        validationSupport.registerValidator(tfDistance, true, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfDistance, context.getFxResources().getString("st.dlg.exercise.error.distance"),
                        !ValidationUtils.isValueDoubleBetween(newValue,
                                exerciseViewModel.sportTypeRecordDistance.get() ? 0.001f : 0, Float.MAX_VALUE)));
        validationSupport.registerValidator(tfAvgSpeed, true, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfAvgSpeed, context.getFxResources().getString("st.dlg.exercise.error.avg_speed"),
                        !ValidationUtils.isValueDoubleBetween(newValue,
                                exerciseViewModel.sportTypeRecordDistance.get() ? 0.001f : 0, Float.MAX_VALUE)));
        validationSupport.registerValidator(tfDuration, true, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfDuration, context.getFxResources().getString("st.dlg.exercise.error.duration"),
                        !ValidationUtils.isValueTimeInSecondsBetween(newValue, 1, Integer.MAX_VALUE)));

        validationSupport.registerValidator(tfAscent, false, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfAscent, context.getFxResources().getString("st.dlg.exercise.error.ascent"),
                        !ValidationUtils.isOptionalValueIntegerBetween(newValue, 0, Integer.MAX_VALUE)));
        validationSupport.registerValidator(tfAvgHeartrate, false, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfAvgHeartrate, context.getFxResources().getString("st.dlg.exercise.error.avg_heartrate"),
                        !ValidationUtils.isOptionalValueIntegerBetween(newValue, 0, 299)));
        validationSupport.registerValidator(tfCalories, false, (Control control, String newValue) ->
                ValidationResult.fromErrorIf(tfCalories, context.getFxResources().getString("st.dlg.exercise.error.calories"),
                        !ValidationUtils.isOptionalValueIntegerBetween(newValue, 0, Integer.MAX_VALUE)));
    }

    /**
     * Initializes all ChoiceBoxes by defining the String converters. ChoiceBoxes with fixed values
     * (SportType, Intensity) will be filled with possible values.
     */
    private void setupChoiceBoxes() {

        cbSportType.setConverter(new NameableStringConverter<>());
        cbSportSubtype.setConverter(new NameableStringConverter<>());
        cbEquipment.setConverter(new NameableStringConverter<>());

        document.getSportTypeList().forEach(sportType -> cbSportType.getItems().add(sportType));
        cbIntensity.getItems().addAll(Arrays.asList(IntensityType.values()));

        // update the sport type dependent choiceboxes on each sport type selection change
        cbSportType.addEventHandler(ActionEvent.ACTION, event -> fillSportTypeDependentChoiceBoxes());

        // reset the previous exercise index for copying the comment on sport type / subtype changes
        cbSportSubtype.addEventHandler(ActionEvent.ACTION, event -> previousExerciseIndex = null);
    }

    /**
     * Fills all ChoiceBoxes with values dependent on the selected sport type.
     */
    private void fillSportTypeDependentChoiceBoxes() {
        cbSportSubtype.getItems().clear();
        cbEquipment.getItems().clear();
        cbEquipment.getItems().add(equipmentNone);

        final SportType selectedSportType = cbSportType.getValue();
        if (selectedSportType != null) {
            selectedSportType.getSportSubTypeList().forEach(sportSubType ->
                    cbSportSubtype.getItems().add(sportSubType));
            selectedSportType.getEquipmentList().forEach(equipment ->
                    cbEquipment.getItems().add(equipment));
        }

        // select equipment "none" when exercise contains no equipment
        if (exerciseViewModel.equipment.get() == null) {
            exerciseViewModel.equipment.set(equipmentNone);
        }

        // disable equipment ChoiceBox when no sport type selected
        cbEquipment.setDisable(selectedSportType == null);
    }

    /**
     * Setup of the automatic calculation of distance, avg speed or duration depending on the
     * user preferences. The selected auto calculation textfield will get disabled. The user can
     * also select another field for auto calculation.<br/>
     * The automatic calculation selection will be disabled when the current sport type does not
     * record the distance.
     */
    private void setupAutoCalculation() {

        // disable the textfields for distance and avg speed when the value is calculated automatically
        // or when the current sport type does not record the distance
        tfDistance.disableProperty().bind(Bindings.or(
                rbAutoCalcDistance.selectedProperty(),
                exerciseViewModel.sportTypeRecordDistance.not()));
        tfAvgSpeed.disableProperty().bind(Bindings.or(
                rbAutoCalcAvgSpeed.selectedProperty(),
                exerciseViewModel.sportTypeRecordDistance.not()));

        // disable the textfield for duration when the value is calculated automatically
        // and when the current sport type records the distance
        tfDuration.disableProperty().bind(Bindings.and(
                rbAutoCalcDuration.selectedProperty(),
                exerciseViewModel.sportTypeRecordDistance));

        rbAutoCalcDistance.selectedProperty().bindBidirectional(exerciseViewModel.autoCalcDistance);
        rbAutoCalcAvgSpeed.selectedProperty().bindBidirectional(exerciseViewModel.autoCalcAvgSpeed);
        rbAutoCalcDuration.selectedProperty().bindBidirectional(exerciseViewModel.autoCalcDuration);

        // disable automatic calculation radioboxes when the current sport type does not record the distance
        rbAutoCalcDistance.disableProperty().bind(exerciseViewModel.sportTypeRecordDistance.not());
        rbAutoCalcAvgSpeed.disableProperty().bind(exerciseViewModel.sportTypeRecordDistance.not());
        rbAutoCalcDuration.disableProperty().bind(exerciseViewModel.sportTypeRecordDistance.not());

        // set initial automatic calculation type from preferences
        switch (document.getOptions().getDefaultAutoCalcuation()) {
            case Distance:
                exerciseViewModel.autoCalcDistance.set(true);
                break;
            case AvgSpeed:
                exerciseViewModel.autoCalcAvgSpeed.set(true);
                break;
            default:
                exerciseViewModel.autoCalcDuration.set(true);
        }
    }

    /**
     * Action handler for selecting an HRM exercise file in the FileChooser.
     */
    @FXML
    private void onBrowseHrmFile(final ActionEvent event) {

        // do we have an initial HRM file for the dialog?
        final String hrmFile = StringUtils.getTrimmedTextOrNull(exerciseViewModel.hrmFile.get());

        // show file open dialog and display selected filename
        final File selectedFile = prHRMFileOpenDialog.get().selectHRMFile(
                getWindow(tfHrmFile), document.getOptions(), hrmFile);
        if (selectedFile != null) {
            exerciseViewModel.hrmFile.set(selectedFile.getAbsolutePath());
        }
    }

    /**
     * Action handler for viewing the selected HRM. It starts the ExerciseViewer sub-application, which
     * parses the HRM file and displays the data.
     */
    @FXML
    private void onViewHrmFile(final ActionEvent event) {

        final String hrmFile = StringUtils.getTrimmedTextOrNull(exerciseViewModel.hrmFile.getValue());
        if (hrmFile != null) {
            prExerciseViewer.get().showExercise(hrmFile, document.getOptions(), getWindow(tfHrmFile), true);
        }
    }

    /**
     * Action handler for importing the exercise data from the selected HRM file.
     */
    @FXML
    private void onImportHrmFile(final ActionEvent event) {

        final String hrmFile = StringUtils.getTrimmedTextOrNull(exerciseViewModel.hrmFile.get());
        if (hrmFile == null) {
            return;
        }

        // parse exercise file
        EVExercise pvExercise = null;
        try {
            ExerciseParser parser = ExerciseParserFactory.getParser(hrmFile);
            pvExercise = parser.parseExercise(hrmFile);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to parse exercise file!", e);
            context.showFxMessageDialog(getWindow(tfHrmFile), Alert.AlertType.ERROR, "common.error",
                    "st.dlg.exercise.error.import_console", hrmFile);
            return;
        }

        // fill dialog widgets with values from parsed HRM exercise
        final LocalDateTime pvExerciseDateTime = pvExercise.getDateTime();
        if (pvExerciseDateTime != null) {
            exerciseViewModel.date.setValue(pvExerciseDateTime.toLocalDate());
            exerciseViewModel.hour.set(pvExerciseDateTime.getHour());
            exerciseViewModel.minute.set(pvExerciseDateTime.getMinute());
        }

        exerciseViewModel.avgHeartRate.set(pvExercise.getHeartRateAVG());
        exerciseViewModel.calories.set(pvExercise.getEnergy());

        int importedDuration = pvExercise.getDuration() / 10;

        // fill speed-related values if available, otherwise just the duration
        if (pvExercise.getSpeed() != null) {
            exerciseViewModel.setAutoCalcFields(
                    pvExercise.getSpeed().getDistance() / 1000f,
                    pvExercise.getSpeed().getSpeedAVG(),
                    importedDuration);
        } else {
            exerciseViewModel.duration.set(importedDuration);
        }

        // fill ascent-related values
        if (pvExercise.getAltitude() != null) {
            exerciseViewModel.ascent.set(pvExercise.getAltitude().getAscent());

            if (document.getOptions().getUnitSystem() == FormatUtils.UnitSystem.English) {
                exerciseViewModel.ascent.set(ConvertUtils.convertMeter2Feet(exerciseViewModel.ascent.get()));
            }
        }
    }


    /**
     * Action handler for copying the comment from the previous similar exercise.
     */
    @FXML
    private void onCopyComment(final ActionEvent event) {

        // get selected sport type and subtype (must be selected)
        final SportType selectedSportType = exerciseViewModel.sportType.get();
        final SportSubType selectedSportSubType = exerciseViewModel.sportSubType.get();

        if (selectedSportType == null || selectedSportSubType == null) {
            context.showFxMessageDialog(getWindow(taComment), Alert.AlertType.ERROR,
                    "common.error", "st.dlg.exercise.error.no_sport_and_subtype");
            return;
        }

        // find the start index for searching the previous exercise when not done yet
        // => get index of current exercise or use the last index when not found
        if (previousExerciseIndex == null) {
            int indexCurrentExercise =
                    document.getExerciseList().indexOf(exerciseViewModel.getExercise());
            if (indexCurrentExercise == -1) {
                indexCurrentExercise = document.getExerciseList().size();
            }
            previousExerciseIndex = indexCurrentExercise;
        }

        // check all exercises before the previous exercise
        int currentSearchIndex = previousExerciseIndex - 1;
        while (currentSearchIndex >= 0) {

            Exercise tempExercise = document.getExerciseList().getAt(currentSearchIndex);

            // has the current checked exercise the same sport type and subtype?
            if ((tempExercise.getSportType().getId() == selectedSportType.getId()) &&
                    (tempExercise.getSportSubType().getId() == selectedSportSubType.getId())) {

                // the current checked exercise is similar => copy comment when there is one
                if (tempExercise.getComment() != null && tempExercise.getComment().length() > 0) {
                    exerciseViewModel.comment.set(tempExercise.getComment());
                    previousExerciseIndex = currentSearchIndex;
                    return;
                }
            }
            currentSearchIndex--;
        }

        // nothing found => delete previous index, so searching will start from the end again
        previousExerciseIndex = null;
        context.showFxMessageDialog(getWindow(taComment), Alert.AlertType.INFORMATION,
                "common.info", "st.dlg.exercise.error.no_previous_exercise");
    }
}
