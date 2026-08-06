package com.supermartijn642.movingelevators.generators;

import com.supermartijn642.core.generator.LanguageGenerator;
import com.supermartijn642.core.generator.ResourceCache;
import com.supermartijn642.movingelevators.MovingElevators;

/**
 * Created 14/02/2022 by SuperMartijn642
 */
public class MovingElevatorsLanguageGenerator extends LanguageGenerator {

    public MovingElevatorsLanguageGenerator(ResourceCache cache){
        super("movingelevators", cache, "en_us");
    }

    @Override
    public void generate(){
        // Moving Elevators' item group
        this.itemGroup(MovingElevators.GROUP, "Moving Elevators");

        // Elevator controller
        this.block(MovingElevators.elevator_block, "Elevator Controller");
        this.translation("movingelevators.elevator_controller.tooltip", "Place elevator controllers above each other to create floors for an elevator");

        // Elevator display
        this.block(MovingElevators.display_block, "Elevator Display");
        this.translation("movingelevators.elevator_display.tooltip", "Shows an elevators' floors when placed on top of an elevator controller or remote elevator panel");

        // Remote elevator panel
        this.block(MovingElevators.button_block, "Remote Elevator Panel");
        this.translation("movingelevators.remote_controller.tooltip", "Can be bound to an elevator controller by right-clicking on it");
        this.translation("movingelevators.remote_controller.tooltip.bound", "Bound to elevator controller at (%1$d, %2$d, %3$d) in %4$s");
        this.translation("movingelevators.remote_controller.bind", "Bound to Elevator Controller!");
        this.translation("movingelevators.remote_controller.not_bound", "The block must be bound to an Elevator Controller!");
        this.translation("movingelevators.remote_controller.wrong_dimension", "The block must be in the same dimension as the Elevator Controller!");
        this.translation("movingelevators.remote_controller.controller_location", "Bound to elevator controller at (%1$d, %2$d, %3$d)");
        this.translation("movingelevators.remote_controller.clear", "Cleared stored elevator location!");

        // Remote elevator display
        this.block(MovingElevators.remote_display_block, "Remote Elevator Display");
        this.translation("movingelevators.remote_display.tooltip", "Shows the floor an elevator is currently at. Bind it to an elevator controller by right-clicking on it");

        // Remote elevator indicator
        this.block(MovingElevators.remote_indicator_block, "Remote Elevator Indicator");
        this.translation("movingelevators.remote_indicator.tooltip", "A slim wall-mounted panel showing the floor an elevator is currently at. Bind it to an elevator controller by right-clicking on it");

        // Remote elevator call panel
        this.block(MovingElevators.remote_call_panel_block, "Remote Elevator Call Panel");
        this.translation("movingelevators.remote_call_panel.tooltip", "A wall-mounted landing panel with a floor readout and up/down call buttons. Bind it to an elevator controller by right-clicking on it");

        // Elevator car panel
        this.block(MovingElevators.elevator_car_panel_block, "Elevator Car Panel");
        this.translation("movingelevators.elevator_car_panel.tooltip", "The panel you ride with: shows the current floor and direction, and opens a floor list when clicked. Bind it to an elevator controller by right-clicking on it");
        this.translation("movingelevators.floor_select.title", "Select a floor");
        this.translation("movingelevators.floor_select.current", "Currently at %s");

        // Elevator doors
        this.block(MovingElevators.elevator_door_block, "Elevator Door");
        this.translation("movingelevators.elevator_door.tooltip", "A 2x2 sliding doorway that opens when the elevator reaches this floor and closes again on its own. Redstone power forces it open. Bind it to an elevator controller by right-clicking on it");
        this.translation("movingelevators.elevator_door.not_bound", "These doors are not bound to an elevator controller!");
        this.translation("movingelevators.elevator_door.no_room", "Not enough room: elevator doors need a 2x2 opening.");
        this.translation("movingelevators.floor_select.door_open", "Open doors");
        this.translation("movingelevators.floor_select.door_close", "Close doors");

        // Floor name
        this.translation("movingelevators.floor_name", "Floor %d");

        // Elevator screen
        this.translation("movingelevators.elevator_screen.cabin_width", "Cabin width");
        this.translation("movingelevators.elevator_screen.cabin_depth", "Cabin depth");
        this.translation("movingelevators.elevator_screen.cabin_height", "Cabin height");
        this.translation("movingelevators.elevator_screen.cabin_width.increase_size", "Increase width");
        this.translation("movingelevators.elevator_screen.cabin_depth.increase_size", "Increase depth");
        this.translation("movingelevators.elevator_screen.cabin_height.increase_size", "Increase height");
        this.translation("movingelevators.elevator_screen.cabin_width.decrease_size", "Decrease width");
        this.translation("movingelevators.elevator_screen.cabin_depth.decrease_size", "Decrease depth");
        this.translation("movingelevators.elevator_screen.cabin_height.decrease_size", "Decrease height");
        this.translation("movingelevators.elevator_screen.cabin_width.increase_offset", "Move right");
        this.translation("movingelevators.elevator_screen.cabin_depth.increase_offset", "Move forward");
        this.translation("movingelevators.elevator_screen.cabin_height.increase_offset", "Move up");
        this.translation("movingelevators.elevator_screen.cabin_width.decrease_offset", "Move left");
        this.translation("movingelevators.elevator_screen.cabin_depth.decrease_offset", "Move backward");
        this.translation("movingelevators.elevator_screen.cabin_height.decrease_offset", "Move down");
        this.translation("movingelevators.elevator_screen.current_floor", "Floor");
        this.translation("movingelevators.elevator_screen.elevator", "Elevator");
        this.translation("movingelevators.elevator_screen.floor_name", "Floor name");
        this.translation("movingelevators.elevator_screen.show_buttons", "Show buttons");
        this.translation("movingelevators.elevator_screen.cabin_size", "Cabin size");
        this.translation("movingelevators.elevator_screen.elevator_speed", "Speed");
        this.translation("movingelevators.elevator_screen.current_speed", "%s blocks/tick");
        this.translation("movingelevators.elevator_screen.display_buttons", "Show buttons: %s");
        this.translation("movingelevators.elevator_screen.display_buttons.on", "True");
        this.translation("movingelevators.elevator_screen.display_buttons.off", "False");

        // Elevator arrive sound
        this.translation("movingelevators.elevator.arrive_sound", "Elevator arrived");

        // Elevator feedback
        this.translation("movingelevators.elevator.invalid_block", "Invalid block '%s' in cabin at %s.");
        this.translation("movingelevators.elevator.empty", "No cabin at the current floor.");
        this.translation("movingelevators.elevator.obstructed", "Cabin space is obstructed by block '%s' at %s.");
        this.translation("movingelevators.elevator.no_cabins", "There are no available cabins.");
    }
}
