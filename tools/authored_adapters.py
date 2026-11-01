"""Lower only named authored culinary functions, not item-name classifiers."""
NATIVE = {'pot': 'pot', 'flex_pot': 'pot', 'stockpot': 'stockpot', 'flex_stockpot': 'stockpot',
          'steamer': 'steamer', 'teapot': 'teapot', 'chopping_board': 'board'}
ADAPTERS = {
    'protected_cold_board_assembly': ('board', 'board', 'board'),
    'protected_cold_board_mix': ('board', 'board', 'board'),
    'protected_cold_board_mix_and_cut': ('board', 'board', 'board'),
    'protected_cold_board_roll_and_cut': ('board', 'board', 'board'),
    'protected_cold_layered_assembly_preserve_components': ('board', 'board', 'board'),
    'protected_cut_inherit_existing_bake': ('board', 'board', 'board'),
    'native_feast_assembly_and_serving_inherit': ('board', 'board', 'board'),
    'native_feast_served_portion_inherit': ('board', 'board', 'board'),
    'native_served_plate_assembly_inherit': ('board', 'board', 'board'),
    'protected_stockpot_heat_finish': ('stockpot', 'stockpot', 'board'),
    'protected_pot_heat_finish': ('pot', 'pot', 'board'),
    'protected_hot_rice_composite': ('pot', 'pot', 'board'),
    'protected_hot_composite': ('pot', 'pot', 'board'),
    'protected_roasting_heat_finish': ('roasting', 'roasting', 'board'),
    'protected_mat_raw_then_bake': ('baking_mat', 'baking', 'baking_mat'),
    'protected_mat_raw_then_bake_then_native_slices': ('baking_mat', 'baking', 'baking_mat'),
    'protected_mat_raw_then_bake_then_provenance_cut': ('baking_mat', 'baking', 'baking_mat'),
    'protected_mat_press_pretoasted_component_no_fictitious_rebake': ('baking_mat', 'board', 'baking_mat'),
    'protected_mat_mix_then_steamer': ('steamer', 'steamer', 'baking_mat'),
    'protected_board_layer_then_bake': ('baking', 'baking', 'board'),
    'protected_native_pot_keep_fixed_ratios_and_seeded_flex': ('pot', 'pot', 'board'),
    'protected_native_rice_composite_inherit_components': ('pot', 'pot', 'board'),
    'protected_native_stockpot_keep_soup_base_and_seeded_flex': ('stockpot', 'stockpot', 'board'),
    'protected_native_stockpot_actual_retention_required': ('stockpot', 'stockpot', 'board'),
    'protected_native_stockpot_keep_seeded_flex': ('stockpot', 'stockpot', 'board'),
    'protected_native_stockpot_simple_flavored_egg': ('stockpot', 'stockpot', 'board'),
    'protected_native_pot_simple_utility_food': ('pot', 'pot', 'board'),
}


def lower_adapter(decision, row):
    adapter = decision.get('adapter')
    if adapter == 'protected_native_rice_composite_inherit_components' and row['type'] in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
        surface = finish = preparation = 'board'
    elif adapter in ADAPTERS:
        surface, finish, preparation = ADAPTERS[adapter]
    elif adapter in ('protected_native_original_finish_not_name_inferred_oven', 'protected_native_stuffed_or_steamed_chain'):
        kind = row['type'].split(':', 1)
        if kind[0] == 'kaleidoscope_cookery' and kind[1] in NATIVE:
            surface = finish = NATIVE[kind[1]]; preparation = 'board'
        elif row['type'] in ('minecraft:smelting', 'minecraft:smoking', 'minecraft:campfire_cooking'):
            surface, finish, preparation = 'baking_mat', 'baking', 'baking_mat'
        else: return None
    elif adapter == 'protected_food_cutting_preserve_all_native_outputs':
        surface = finish = preparation = 'board'
    else: return None
    result = {'surface': surface, 'finish': finish, 'preparation_surface': preparation}
    if row['type'] in ('farmersdelight:cutting', 'kaleidoscope_cookery:chopping_board'):
        result['action'] = 'cut'; result['tool_tag'] = 'forge:tools/knives'
    elif adapter in ('protected_cold_board_mix', 'protected_mat_mix_then_steamer'):
        result['action'] = 'mix'
    elif adapter == 'protected_cold_board_roll_and_cut': result['action'] = 'roll'
    elif adapter == 'protected_mat_press_pretoasted_component_no_fictitious_rebake': result['action'] = 'roll'
    tool = (row.get('raw') or {}).get('tool')
    if isinstance(tool, dict) and isinstance(tool.get('tag'), str): result['tool_tag'] = tool['tag']
    return result
