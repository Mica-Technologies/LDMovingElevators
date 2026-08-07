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
        this.translation("movingelevators.remote_call_panel.tooltip", "A wall-mounted landing panel with a floor readout and up/down call buttons. Bind it to an elevator controller by right-clicking on it. Right-click a controller in each of several shafts to have one panel serve them all \u2014 a press then sends whichever of them is best placed to answer. Right-click a controller again to unlink it; sneak and right-click the air to empty the item. Sneak-click a placed panel to see what it is linked to");
        this.translation("movingelevators.remote_call_panel.status.header", "Call panel \u2014 landing %s");

        // Elevator car panel
        this.block(MovingElevators.elevator_car_panel_block, "Elevator Car Panel");
        this.translation("movingelevators.elevator_car_panel.tooltip", "The panel you ride with: shows the current floor and direction, and opens a floor list when clicked. Bind it to an elevator controller by right-clicking on it");
        this.translation("movingelevators.floor_select.title", "Select a floor");
        this.translation("movingelevators.floor_select.current", "Currently at %s");

        // Bank car panel
        this.block(MovingElevators.bank_car_panel_block, "Elevator Bank Car Panel");
        this.translation("movingelevators.bank_car_panel.tooltip", "The panel you ride with in an elevator bank: shows the current floor and direction, and opens the doors or rings the alarm when clicked. It has no floor buttons, because in a bank you enter your destination at the lobby panel before you board. Bind it to an elevator controller by right-clicking on it");
        // Titles for the controls the bank car panel does offer. Two keys rather than one with an
        // optional argument, because an unnamed elevator should not leave a gap in the title.
        this.translation("movingelevators.car_controls.title", "Elevator");
        this.translation("movingelevators.car_controls.title_named", "Elevator %s");

        // Bank lobby panel
        this.block(MovingElevators.bank_lobby_panel_block, "Elevator Bank Lobby Panel");
        this.translation("movingelevators.bank_lobby_panel.tooltip", "Link it to ONE elevator controller in each shaft it should serve \u2014 right-click that controller with it. The panel finds the rest of the shaft's floors, and its own landing, by itself, so there is no need to link every floor. Right-click a controller again to unlink it. The item keeps its links after you place a panel, so one lobby panel per floor takes no extra work; sneak and right-click the air to empty it for a different bank. Sneak-click a placed panel to see what it is linked to");
        this.translation("movingelevators.bank_lobby.title", "Enter your destination floor");
        this.translation("movingelevators.bank_lobby.unbound", "This panel isn't linked to any elevators yet");
        this.translation("movingelevators.bank_lobby.no_car", "No linked elevator can reach that floor");
        this.translation("movingelevators.bank_lobby.dispatched", "An elevator is on its way \u2014 going to %s");
        this.translation("movingelevators.bank_lobby.dispatched_named", "Elevator %s is on its way \u2014 going to %s");
        this.translation("movingelevators.bank_lobby.ready", "An elevator is ready to board \u2014 going to %s");
        this.translation("movingelevators.bank_lobby.ready_named", "Elevator %s is ready to board \u2014 going to %s");
        this.translation("movingelevators.bank_lobby.floors_more", "%s, %s");
        this.translation("movingelevators.bank_lobby.floors_last", "%s and %s");
        this.translation("movingelevators.elevator_screen.elevator_name", "Elevator name");

        this.translation("movingelevators.bank_lobby_panel.status.header", "Bank lobby panel \u2014 landing %s");
        this.translation("movingelevators.bank_lobby_panel.status.none", "  Not linked to any elevator. Right-click one controller in each shaft with a panel item, then place the panel.");
        this.translation("movingelevators.bank_lobby_panel.status.elevator", "  Elevator %s: shaft at %s, serving %s floors");
        this.translation("movingelevators.bank_lobby_panel.status.missing", "  Elevator %s: the controller at %s is gone");
        this.translation("movingelevators.bank_lobby_panel.status.aligned", "  Linked elevators agree on every floor they share.");
        this.translation("movingelevators.bank_lobby_panel.status.mismatch", "  These elevators disagree about a shared floor's name or height. Shafts may serve different floors, but the ones they share must line up.");
        this.translation("movingelevators.bank_lobby_panel.misaligned", "Not linked: that elevator disagrees with one already linked about a floor \u2014 %s. Shafts in a bank may serve different floors, but a floor they share must have the same name and the same height.");
        this.translation("movingelevators.bank_lobby_panel.bound", "Elevator linked. This panel now serves %s elevator(s)");
        this.translation("movingelevators.bank_lobby_panel.unbound_one", "Elevator unlinked. This panel now serves %s elevator(s)");
        this.translation("movingelevators.bank_lobby_panel.copied", "Copied. This panel item now serves %s elevator(s)");

        // Elevator doors
        this.block(MovingElevators.elevator_door_block, "Elevator Door");
        this.translation("movingelevators.elevator_door.tooltip", "A 2x2 sliding doorway. Place it at an elevator landing and it finds that elevator by itself: it opens when the cabin arrives and closes again on its own. Redstone power forces it open");
        this.block(MovingElevators.elevator_single_door_block, "Elevator Door (Narrow)");
        this.translation("movingelevators.elevator_single_door.tooltip", "A 1x2 sliding doorway. Place it at an elevator landing and it finds that elevator by itself: it opens when the cabin arrives and closes again on its own. Redstone power forces it open");
        this.translation("movingelevators.elevator_door.status.header", "Elevator door status:");
        this.translation("movingelevators.elevator_door.status.searching", "  No elevator landing found within %s blocks at this height.");
        this.translation("movingelevators.elevator_door.status.bound", "  Bound to controller at (%1$s, %2$s, %3$s)");
        this.translation("movingelevators.elevator_door.status.no_group", "  That controller is gone, or belongs to a different elevator.");
        this.translation("movingelevators.elevator_door.status.landing", "  Door at y=%1$s serves landing y=%2$s");
        this.translation("movingelevators.elevator_door.status.cabin", "  Cabin at this landing: %1$s (cabin is on floor index %2$s)");
        this.translation("movingelevators.elevator_door.status.half", "  Upper half: %1$s, open ticks left: %2$s");
        this.translation("movingelevators.elevator_door.status.requests", "  Open now: %1$s (unseen open request: %2$s, close: %3$s)");
        this.translation("movingelevators.elevator_door.not_bound", "These doors are not bound to an elevator controller!");
        this.translation("movingelevators.elevator_door.no_room", "Not enough room for the doorway.");
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
        this.translation("movingelevators.elevator_screen.cabin_size", "Cabin size");
        this.translation("movingelevators.elevator_screen.elevator_speed", "Speed");
        this.translation("movingelevators.elevator_screen.current_speed", "%s blocks/tick");
        this.translation("movingelevators.elevator_screen.sounds", "Sounds: %s");
        this.translation("movingelevators.elevator_screen.sounds.off", "Off");
        this.translation("movingelevators.elevator_screen.hide_controls", "Hide controls: %s");
        this.translation("movingelevators.elevator_screen.hide_controls.on", "True");
        this.translation("movingelevators.elevator_screen.hide_controls.off", "False");

        // Elevator arrive sound
        this.translation("movingelevators.elevator.arrive_sound", "Elevator stops");
        this.translation("movingelevators.elevator.passing_floor_sound", "Elevator passes a floor");
        this.translation("movingelevators.elevator.arrive_ding_sound", "Elevator chimes");
        this.translation("movingelevators.elevator.door_open_sound", "Elevator doors open");
        this.translation("movingelevators.elevator.door_close_sound", "Elevator doors close");

        this.translation("movingelevators.elevator_screen.sound_scheme", "Sound scheme");
        this.translation("movingelevators.sound_scheme.standard", "Standard");
        this.translation("movingelevators.sound_scheme.modern", "Modern");

        this.translation("movingelevators.elevator_screen.out_of_service", "Out of service: %s");
        this.translation("movingelevators.elevator_screen.out_of_service.yes", "True");
        this.translation("movingelevators.elevator_screen.out_of_service.no", "False");
        this.translation("movingelevators.service_mode.normal", "Normal service");
        this.translation("movingelevators.service_mode.independent", "Independent service");
        this.translation("movingelevators.service_mode.out_of_service", "Out of service");
        this.translation("movingelevators.service_mode.not_allowed", "Looks like this requires a key I don't have...");
        this.translation("movingelevators.floor_select.independent", "Independent service: %s");
        this.translation("movingelevators.floor_select.independent.what", "Answers its own buttons only, not the floors");
        this.translation("movingelevators.floor_select.independent.key", "Turning it requires permission");
        // Two letters, because it shares a readout built for floor numbers.
        this.translation("movingelevators.independent_service.short", "IS");
        this.translation("movingelevators.out_of_service.marquee", "OUT OF SERVICE");
        this.translation("movingelevators.fire_recall.marquee", "FIRE RECALL");
        this.item(MovingElevators.alarm_linker, "Elevator Alarm Linker");
        this.translation("movingelevators.alarm_linker.tooltip", "SNEAK and right-click a fire alarm panel, then right-click an elevator controller. The elevator returns to that controller's floor whenever that alarm sounds. Sneak and right-click the air to forget the panel");
        this.translation("movingelevators.alarm_linker.tooltip.holding", "Holding fire alarm panel at %s \u2014 now right-click an elevator controller");
        this.translation("movingelevators.alarm_linker.selected", "Fire alarm panel selected at %s \u2014 now right-click an elevator controller");
        this.translation("movingelevators.alarm_linker.no_panel", "Sneak and right-click a fire alarm panel first");
        this.translation("movingelevators.alarm_linker.no_elevator", "That controller is not part of an elevator yet");
        this.translation("movingelevators.alarm_linker.paired", "Elevator paired to the fire alarm panel at %s");
        this.translation("movingelevators.alarm_linker.cleared", "Forgot the fire alarm panel");
        this.translation("movingelevators.service_mode.fire_recall", "Fire recall");

        // Two halves of the flashing emergency readout, abbreviating "emergency stop". Separate keys
        // rather than one string because a translation may not split at the same point English does.
        this.translation("movingelevators.emergency.flash_first", "E");
        this.translation("movingelevators.emergency.flash_second", "ST");

        this.translation("movingelevators.elevator.alarm_sound", "Elevator alarm rings");
        this.translation("movingelevators.elevator.overload_sound", "Elevator overloaded");
        // Scrolled two characters at a time across a floor readout, so a translation wants to be
        // short and to read in pairs.
        this.translation("movingelevators.overload.marquee", "OVERLOAD");
        this.translation("movingelevators.floor_select.emergency_stop", "Emergency stop");
        this.translation("movingelevators.floor_select.alarm", "Alarm");
        this.translation("movingelevators.floor_select.alarm.tooltip", "Hold to ring the alarm");

        // Elevator feedback
        this.translation("movingelevators.elevator.invalid_block", "Invalid block '%s' in cabin at %s.");
        this.translation("movingelevators.elevator.empty", "No cabin at the current floor.");
        this.translation("movingelevators.elevator.obstructed", "Cabin space is obstructed by block '%s' at %s.");
        this.translation("movingelevators.elevator.no_cabins", "There are no available cabins.");
    }
}
