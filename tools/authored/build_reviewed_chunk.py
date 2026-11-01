#!/usr/bin/env python3
"""Reproduce this finite, explicitly enumerated source-review chunk; not a classifier.

Changing the named lists or reviewed source hashes is a new authored review.
No output is native qualification. Native counts/effects/composite yields stay gaps.
"""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FROZEN = json.loads((ROOT / 'frozen/catalogue_inputs.json').read_text())
DRINKS = Path('/home/dev/mod_source/better-drinking-water/src/main/resources/drinks.json')
DRINK_ROWS = json.loads(DRINKS.read_text())['drinks']
LOCK = json.loads((ROOT / 'authored/source-lock.json').read_text())
DOC = {'schema': 'better_cooking:authored_decisions.v1', 'revision': 2, 'families': [], 'foods': {}, 'routes': {}, 'source_inputs': {}}


def pin(path):
    path = Path(path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if LOCK.get(str(path)) != digest:
        raise ValueError('reviewed source changed; explicitly re-review decisions/source-lock before rebuilding: ' + str(path))
    DOC['source_inputs'][str(path)] = digest


def family(key, namespace, names, role, work, adapter, reason, moisture=None):
    items = [namespace + ':' + name for name in names.split()]
    if any(item not in FROZEN['foods'] for item in items): raise ValueError('reviewed ID missing from frozen declaration: ' + str(set(items) - set(FROZEN['foods'])))
    decision = {'role': role, 'work_class': work, 'adapter': adapter, 'reviewed': ['role', 'work_class', 'adapter']}
    if moisture is not None:
        decision['moisture'] = {'hydration': moisture[0], 'quenching': moisture[1], 'class': moisture[2]}
        decision['reviewed'].append('moisture')
    evidence = {}
    for item in items:
        evidence[item] = list(FROZEN['foods'][item]['evidence'])
        evidence[item] += [ref for row in FROZEN['recipes'].values() if row.get('raw') and any(out.get('id') == item for out in row['outputs']) for ref in row['evidence']]
    DOC['families'].append({'id': key, 'items': items, 'decision': decision, 'reason': reason, 'evidence': ['IMPLEMENTATION.md#native-exceptions-standard-work-classes-and-source-ratios'], 'item_evidence': evidence})


# This exact finite provider matrix is authored upstream, not inferred from item names.
pin(DRINKS)
DOC['families'].append({'id': 'managed-paid-beverages-v1', 'items': [r['id'] for r in DRINK_ROWS],
    'decision': {'role': 'drink', 'work_class': 'none', 'adapter': 'provider_paid_beverage', 'owner': 'better_drinking_water', 'reviewed': ['role', 'work_class', 'owner', 'moisture']},
    'reason': 'The explicit provider-owned drink matrix defines class, serving volume and paid dose; do not duplicate its credit through culinary food completion.',
    'evidence': [str(DRINKS) + '#version=1'], 'item_evidence': {r['id']: [str(DRINKS) + '#drinks/' + r['id']] for r in DRINK_ROWS}})
for row in DRINK_ROWS:
    DOC['foods'][row['id']] = {'moisture': {'hydration': row['hydration'], 'quenching': row['quenching'], 'class': row['hydration_class'], 'serving_mb': row['serving_mb']},
        'reason': f"{row['id']}: authored {row['hydration_class']} {row['serving_mb']}mB serving, exact paid dose {row['hydration']}/{row['quenching']}; ownership remains beverage provider, not prepared meal.",
        'evidence': [str(DRINKS) + '#drinks/' + row['id']]}

FD = 'farmersdelight'
family('fd-raw-cultivar-produce', FD, 'cabbage cabbage_leaf onion tomato pumpkin_slice', 'ingredient', 'none', 'retain_native_crop_ingredient',
       'Named cultivated produce/cut leaves are ingredient/staple exceptions, not manufactured human meals. Canonical FD/Bumblezone origin remains required; existing native nutrition is not meal-rebalanced.', (1, 1.25, 'authored_moist_vegetable'))
family('fd-raw-animal-cuts', FD, 'bacon chicken_cuts cod_slice ham minced_beef mutton_chops salmon_slice', 'staple', 'none', 'native_animal_staple_protected_cutting',
       'Explicit native animal cuts retain native intake/effects and ingredient utility. Food-producing knife cuts still migrate to protected board work; nonfood knife recipes do not.', (.5, .625, 'authored_meat'))
family('fd-basic-cooked-animal-staples', FD, 'beef_patty cooked_bacon cooked_chicken_cuts cooked_cod_slice cooked_mutton_chops cooked_salmon_slice fried_egg smoked_ham', 'staple', 'none', 'retain_native_basic_heat_staple',
       'These exact plain cooked cuts/egg are native staple heat transformations, not authored composite meals. Keep useful bulk/native cooking and signed/effect semantics.', (.5, .625, 'authored_meat'))
family('fd-plain-cooked-grain', FD, 'cooked_rice', 'staple', 'none', 'retain_native_basic_heat_staple',
       'Current pinned cooking recipe is one rice crop to one plain cooked-rice bowl; basic cooked grains remain native staples, not composite prepared meals.', (1, 1.25, 'authored_cooked_grain'))
family('fd-basic-dough-pasta-ingredients', FD, 'wheat_dough raw_pasta pie_crust', 'ingredient', 'none', 'canonical_bulk_or_native_ingredient',
       'Explicit flour/dough/pasta/crust ingredient products remain useful bulk inputs; equivalent raw dough canonicalizes to KC with exact original count/chance, not deletion. Native consumed moisture is still unresolved where not evidenced.')
family('fd-explicit-pet-feed', FD, 'dog_food', 'special', 'none', 'retain_native_nonhuman_feed',
       'The pinned recipe and intended dog-feed item are animal feed, not a manufactured player meal. Preserve native consumability/effects without claiming a culinary preparation reward.')
family('fd-cold-fruit-leaf-salads', FD, 'fruit_salad gleaming_salad mixed_salad nether_salad', 'meal', 'straightforward', 'protected_cold_board_assembly',
       'Named recipes assemble multiple actual fruit/leaf components in a bowl without a native heat step. Board assembly is straightforward (30s/eight portions); source ingredient vectors/yields, native effects and composite moisture remain independently required.', (2, 2.5, 'authored_salad_default_pending_composite_derivation'))
family('fd-cold-meat-sandwiches', FD, 'bacon_sandwich chicken_sandwich egg_sandwich hamburger mutton_wrap', 'meal', 'substantial', 'protected_cold_board_assembly',
       'These exact recipes combine previously prepared animal components, bread/wrap and vegetables; retain all paid source components. Substantial layered assembly is 90s/eight portions, not a free crafting-table meal. Composite provenance remains unproved.')
family('fd-liquid-soups', FD, 'baked_cod_stew beef_stew chicken_soup fish_stew noodle_soup onion_soup pumpkin_soup vegetable_soup', 'meal', 'substantial', 'protected_stockpot_heat_finish',
       'Pinned FD cooking recipes explicitly produce these served soup/stew bowls from actual ingredient groups. Preserve exact inputs/result counts, require protected preparation plus heat; default liquid dose is not evidence of unobserved added water or retention.', (4, 5, 'authored_liquid_soup_default_pending_composite_derivation'))
family('fd-basic-bone-broth', FD, 'bone_broth', 'meal', 'straightforward', 'protected_stockpot_heat_finish',
       'Pinned bone broth is a simple prepared broth, not a crop/animal staple exception. Use straightforward stirring plus actual native heat; retain the bone ingredient and unresolved vessel/composite facts.', (4, 5, 'authored_liquid_soup_default_pending_composite_derivation'))
family('fd-cooked-grain-composites', FD, 'fried_rice mushroom_rice vegetable_noodles pasta_with_meatballs pasta_with_mutton_chop squid_ink_pasta', 'meal', 'substantial', 'protected_pot_heat_finish',
       'Each enumerated pinned cooking recipe combines grain/pasta with separately counted vegetable/animal components. Substantial active preparation plus native heat, preserving actual source batch proportions; never convert every pot output into plain rice.', (1, 1.25, 'authored_cooked_grain_default_pending_composite_derivation'))
family('fd-cooked-composite-main-dishes', FD, 'bacon_and_eggs barbecue_stick cabbage_rolls dumplings grilled_salmon ratatouille roasted_mutton_chops steak_and_potatoes stuffed_potato', 'meal', 'substantial', 'protected_hot_composite',
       'These exact pinned recipes are composite cooked dishes with more than a plain staple transformation. Substantial active preparation retains their actual ingredients, carriers and native finish; surface/count/fluid facts remain route-specific.')
family('fd-rice-seaweed-rolls', FD, 'cod_roll salmon_roll kelp_roll kelp_roll_slice', 'meal', 'substantial', 'protected_cold_board_roll_and_cut',
       'Pinned rice/fish/seaweed assembly and exact cutting outputs prove prepared rolls, not bulk rice. Keep original roll and slice yields, age and inherited quality; cutting alone does not re-earn the full dish work reward.', (1, 1.25, 'authored_cooked_grain_default_pending_composite_derivation'))
family('fd-small-sweet-bakes', FD, 'honey_cookie sweet_berry_cookie', 'meal', 'straightforward', 'protected_mat_raw_then_bake',
       'Explicit sweetened cookie recipes are manufactured desserts rather than basic bread staples. Meaningful mat preparation creates a raw stage, then native baking; actual result count is retained, never invented eight.', (.25, .3125, 'authored_soft_bake_default_pending_composite_derivation'))
family('fd-cold-dessert-portions', FD, 'glow_berry_custard melon_popsicle', 'meal', 'straightforward', 'protected_cold_board_assembly',
       'Exact pinned multi-ingredient cold desserts require protected mixing/assembly, not a fictitious oven. Retain their real bottles/sticks and effects; frozen/fruit ingredient moisture still needs counted composite provenance.')
family('fd-whole-pies-and-cheesecake', FD, 'apple_pie chocolate_pie sweet_berry_cheesecake', 'meal', 'elaborate', 'protected_mat_raw_then_bake_then_native_slices',
       'Exact whole pie/cheesecake recipes combine crust/filling components. Elaborate mat work plus baking, with whole-item and slice portion counts proved separately; do not declare an unobserved placed bite count.', (.25, .3125, 'authored_soft_bake_default_pending_composite_derivation'))
family('fd-existing-baked-slices', FD, 'apple_pie_slice cake_slice chocolate_pie_slice pumpkin_pie_slice sweet_berry_cheesecake_slice', 'meal', 'elaborate', 'protected_cut_inherit_existing_bake',
       'Pinned cutting results are edible slices of an already prepared/baked whole item. Protect knife slicing and inherit oldest age/worst quality; no rebake, reroll or nutrient double credit. Native item/slice counts stay as observed in each source route.', (.25, .3125, 'authored_soft_bake_default_pending_composite_derivation'))
family('fd-served-feast-portions', FD, 'honey_glazed_ham roast_chicken shepherds_pie stuffed_pumpkin', 'meal', 'elaborate', 'native_feast_served_portion_inherit',
       'Named served foods belong to exact native multi-component feast products, not plain animal staples. Preserve portion intake and parent provenance; producing servings must not reroll quality or charge the full feast work again.')
family('fd-placed-feast-candidates', FD, 'honey_glazed_ham_block roast_chicken_block shepherds_pie_block stuffed_pumpkin_block rice_roll_medley_block', 'meal', 'elaborate', 'protected_feast_then_native_placed_completion',
       'Pinned registered feast-block identities and full recipes prove intended prepared feasts. This role review is NOT edible-item truth or a placed bite/count assertion: native intake, servings and complete provenance remain explicit gaps.')

family('fd-cooked-sauce-ingredient', FD, 'tomato_sauce', 'ingredient', 'none', 'retain_native_culinary_sauce_ingredient',
       'Pinned tomato sauce is a bulk sauce ingredient used in later composite dishes, not an automatic meal reward. Keep its actual cooking inputs and native consumed properties; no inferred added water.')

UBE = 'ubesdelight'
family('ube-native-crop-ingredients', UBE, 'garlic garlic_chop ginger ginger_chop lemongrass ube', 'ingredient', 'none', 'native_crop_ingredient_and_protected_food_cutting',
       'Exact native roots/herbs and their knife-chopped ingredients remain native ingredients. Pinned food-cutting routes migrate while nonfood cuts and existing crop acquisition authority remain intact.', (1, 1.25, 'authored_moist_vegetable'))
family('ube-native-sauce-dairy-ingredients', UBE, 'condensed_milk_bottle fish_sauce_bottle', 'ingredient', 'none', 'retain_native_culinary_dairy_sauce_ingredient',
       'Pinned cooking outputs are concentrated dairy/sauce ingredients used by named recipes. Preserve useful ingredient production, actual containers and native dose; beverage ownership/composite liquid retention is not guessed.')
family('ube-raw-enriched-pastry-products', UBE, 'ensaymada_raw ensaymada_ube_raw hopia_munggo_raw hopia_ube_raw pandesal_raw pandesal_ube_raw', 'ingredient', 'none', 'protected_raw_mat_product_then_native_bake_chain_required',
       'These exact raw enriched pastry products are source-proven intermediate outputs, NOT final baked desserts or plain equivalent bulk dough. Protect original mat formulation then actual native baking with every chance output retained. Chain adapter remains blocked, not a fake raw-product bake result.')
family('ube-finished-enriched-pastries', UBE, 'ensaymada ensaymada_ube hopia_munggo hopia_ube pandesal pandesal_ube', 'meal', 'substantial', 'protected_raw_mat_product_then_native_bake_chain_required',
       'The pinned raw-mat recipes and exact smelting/smoking/campfire links prove enriched multi-component pastries. Substantial mat work plus real native bake, preserving literal yields and original raw stage/bonus probability; no direct crafting/mat final-pastry shortcut.', (.25, .3125, 'authored_soft_bake_default_pending_composite_derivation'))
family('ube-sweet-cookie-desserts', UBE, 'cookie_ginger cookie_ube', 'meal', 'straightforward', 'protected_mat_raw_then_bake',
       'Exact sweetened flavored-cookie recipes are manufactured desserts, not plain bread. Protect meaningful mat work and require baking; preserve all source result/chance counts.', (.25, .3125, 'authored_soft_bake_default_pending_composite_derivation'))
family('ube-pressed-polvorone-desserts', UBE, 'polvorone polvorone_cc polvorone_pinipig polvorone_ube', 'meal', 'substantial', 'protected_mat_press_pretoasted_component_no_fictitious_rebake',
       'Pinned polvorone mat recipes press already prepared raw_polvorone components and variants. Preserve their actual tool/stage and multiple result probabilities; do not invent another oven requirement for this source-proven pressed sweet.', (.25, .3125, 'authored_soft_sweet_default_pending_composite_derivation'))
family('ube-hot-composite-rice-meals', UBE, 'arroz_caldo bangsilog chicken_inasal_rice sinangag tosilog', 'meal', 'substantial', 'protected_hot_rice_composite',
       'Named pinned FD cooking recipes combine actual rice with animal/aromatic components. Protect substantial preparation and native heat, preserving each literal result yield and ingredient alternatives.', (1, 1.25, 'authored_cooked_grain_default_pending_composite_derivation'))
family('ube-hot-composite-protein-meals', UBE, 'bulalo chicken_inasal lumpia mechado sisig tocino', 'meal', 'substantial', 'protected_hot_composite',
       'Each exact pinned cooking recipe proves a mixed prepared dish, not a plain raw/cooked animal staple. Preserve source components, carriers and actual heat; moisture/effects/native delivery still need independent evidence.')
family('ube-cold-citrus-fish-assembly', UBE, 'kinilaw', 'meal', 'substantial', 'protected_cold_board_mix',
       'Pinned crafting recipe combines raw fish and citrus/aromatics without an oven. Protect actual cold mixing; do not invent curing/aging/preservation machinery or thermal finish.')
family('ube-layered-cold-halo-halo', UBE, 'halo_halo', 'meal', 'elaborate', 'protected_cold_layered_assembly_preserve_components',
       'Exact source recipe combines prepared flan/concentrated milk with ube and fruit. Treat actual layered dessert assembly, not a newly invented liquid drink or oven bake; preserve all paid components and inherited provenance.')
family('ube-steamed-custard', UBE, 'leche_flan', 'meal', 'substantial', 'protected_mat_mix_then_steamer',
       'The explicit custard product needs protected mixing and actual steam finish rather than direct final dessert crafting; retain native source inputs/yield and unresolved vessel semantics.')
family('ube-whole-cake-and-slice', UBE, 'ube_cake ube_cake_slice', 'meal', 'elaborate', 'protected_mat_raw_then_bake_then_provenance_cut',
       'Pinned cake formulation and knife slice source prove elaborate composite cake preparation and inherited slicing. Whole count/bites remain native gaps; preserve chance byproducts and require raw-stage baking.', (.25, .3125, 'authored_soft_bake_default_pending_composite_derivation'))
family('ube-cold-placed-feast-candidates', UBE, 'halo_halo_feast leche_flan_feast lumpia_feast leaf_feast leaf_feast_cooked_rice_half leaf_feast_ensaymada_half leaf_feast_ensaymada_ube_half leaf_feast_fried_rice_half leaf_feast_hopia_munggo_half leaf_feast_hopia_ube_half leaf_feast_pandesal_half leaf_feast_pandesal_ube_half leaf_feast_sinangag_half', 'meal', 'elaborate', 'native_feast_assembly_and_serving_inherit',
       'Explicit registered display/feast identities and source assembly/splitting recipes represent actual prepared feast vessels. This review does not assert edible item truth, unobserved portions or a hot finish for arranging existing cooked portions; native placed completion must prove them.')
family('ube-placed-beverage-vessel', UBE, 'milk_tea_ube_feast', 'drink', 'none', 'native_beverage_vessel_paid_provider_dispatch_pending',
       'Source craft assembles a milk-tea display/vessel from the managed beverage, not a manufactured ordinary food meal. Prevent culinary food credit; actual native vessel debit/servings/provider dispatch remain unresolved.')

KC = 'kaleidoscope_cookery'
family('kc-legacy-duplicate-crop-ingredients', KC, 'lettuce tomato', 'ingredient', 'none', 'canonical_new_crop_acquisition_retain_legacy_stock',
       'Exact duplicate cultivated produce uses canonical FD cabbage/tomato for NEW acquisition. Registered legacy stock remains usable through exact-count ingredient alternatives; native nutrition stays intact.', (1, 1.25, 'authored_moist_vegetable'))
family('kc-distinct-native-chilies', KC, 'green_chili red_chili', 'ingredient', 'none', 'retain_distinct_native_chili_and_spice_dose',
       'Explicit native green/red chilies are distinct crops, NOT guessed FD aliases. Preserve original ingredient/spice-dose and acquisition facts; ordinary nutrient credit never includes Spice.', (1, 1.25, 'authored_moist_vegetable'))
family('kc-native-raw-animal-cuts', KC, 'raw_cow_offal raw_cut_small_meats raw_lamb_chops raw_pork_belly', 'staple', 'none', 'retain_native_raw_staple_protected_food_cutting',
       'Named native raw animal cuts retain ordinary/native properties and harmful effects. They are ingredients/staples rather than finished composite meals; food-cut routes still require protected knife work.', (.5, .625, 'authored_meat'))
family('kc-native-plain-cooked-cuts', KC, 'cooked_cow_offal cooked_cut_small_meats cooked_lamb_chops cooked_pork_belly fried_egg', 'staple', 'none', 'retain_native_basic_heat_staple',
       'These source-proven plain cooked animal/egg transformations remain native staple heat cooking. Do not promote basic protein cuts into elaborate meals or erase their native exceptions.', (.5, .625, 'authored_meat'))
family('kc-native-plain-grain-bread', KC, 'cooked_rice mantou', 'staple', 'none', 'retain_native_basic_heat_grain_bread',
       'Exact plain rice/steamed dough recipes are basic cooked-grain/bread staples with native bulk utility. They are not enriched filled/dessert products; retain the actual native heat route.')
family('kc-raw-shaped-meatball-ingredient', KC, 'raw_meatball', 'ingredient', 'none', 'protected_board_formed_raw_product_then_native_heat_chain_required',
       'Pinned raw meatball recipe pays two raw-meat units plus vegetables: shaped composite intermediate, NOT an unworked plain meat cut. Protect forming and preserve original count before native cooking; unresolved raw-product chain blocks migration.')
family('kc-cooked-composite-meatball', KC, 'cooked_meatball', 'meal', 'substantial', 'protected_raw_formed_product_then_native_heat_chain_required',
       'Source-proven cooked form of the meat-plus-vegetable composite retains raw formulation provenance and native finish; it is not a plain animal staple. Chain proof is still required.')
family('kc-native-raw-insect-exception', KC, 'caterpillar', 'special', 'none', 'retain_native_insect_food_exception',
       'Pinned ModFoods.CATERPILLAR retains native hunger18/saturation.2 and guaranteed nausea200ticks. This raw animal/insect exception is not a standardized manufactured meal.')
family('kc-fried-insect-mobility-utility', KC, 'fried_caterpillar', 'meal', 'straightforward', 'protected_native_pot_simple_utility_food',
       'Actual source disproves plain-fry exemption: FRIED_CATERPILLAR_ITEM is hunger12/saturation.8 with guaranteed FLATULENCE200ticks; BaseEffect marks BENEFICIAL and FlatulenceEvent gives +.75Y velocity on each shift press. Valuable renewable mobility therefore requires meaningful straightforward pot preparation, retaining original fixed/flex inputs, world-seeded evaluator and three SOURCE-PROVEN portions. Positive effects still require native bounding.', (.5, .625, 'authored_animal_food_default_pending_composite_derivation'))
family('kc-native-failed-dish-exception', KC, 'dark_cuisine', 'special', 'none', 'retain_native_failed_preparation_product',
       'Pinned native failure/dark-cuisine item is an actual poor-result exception, not an ordinary 6/8/10 hunger meal. Preserve actual native effects and do not reroll poor quality into a standardized reward.')
family('kc-cold-sashimi-assemblies', KC, 'sashimi cold_style_sashimi desert_style_sashimi end_style_sashimi nether_style_sashimi tundra_style_sashimi golden_salad cold_roasted_meat', 'meal', 'substantial', 'protected_cold_board_mix_and_cut',
       'These exact native cold assemblies/sashimi styles have real source components and knife/assembly function. Preserve their native exceptions/inherited quality; do not infer a furnace from a thematic name or add fake curing equipment.')
family('kc-fixed-and-flex-pan-composites', KC, 'blaze_lamb_chop braised_beef braised_pork_ribs candied_potato chorus_fried_egg crystal_lamb_chop dongpo_pork donkey_burger fish_flavored_shredded_pork fondant_spider_eye frost_lamb_chop oil_splashed_fish pan_seared_knight_steak scramble_egg_with_tomatoes slime_ball_meal spicy_chicken spicy_rabbit_head stir_fried_pork_with_peppers stuffed_tiger_skin_pepper sweet_and_sour_ender_pearls sweet_and_sour_pork suspicious_stir_fry', 'meal', 'substantial', 'protected_native_pot_keep_fixed_ratios_and_seeded_flex',
       'Enumerated source fixed/flexible native pan dishes are manufactured composites. Keep actual fixed recipe ratios; flexible routes retain nine physical count-one units, actual world seed/paid inputs and native evaluator. Worst inherited quality never rerolls/compounds.')
family('kc-elaborate-pan-pie-sweets', KC, 'fondant_pie fried_spring_roll meat_pie samsa stargazy_pie sticky_candy sticky_rice_cake', 'meal', 'elaborate', 'protected_native_original_finish_not_name_inferred_oven',
       'These exact source composite/filled/sweet pan products retain their ORIGINAL native finish (including pan routes for pies), actual counts and paid components. Elaborate work is 240s/eight portions; no name-derived duplicate oven or invented batch of eight.')
family('kc-native-rice-bowl-assemblies', KC, 'braised_beef_rice_bowl fish_flavored_shredded_pork_rice_bowl scramble_egg_with_tomatoes_rice_bowl stir_fried_pork_with_peppers_rice_bowl sweet_and_sour_pork_rice_bowl egg_fried_rice', 'meal', 'substantial', 'protected_native_rice_composite_inherit_components',
       'Pinned bowl/pan recipes combine actual prepared dish/grain components. Retain source counts/carriers and inherited age/quality, not a second full dish nutrition credit.', (1, 1.25, 'authored_cooked_grain_default_pending_composite_derivation'))
family('kc-native-liquid-stockpot-soups', KC, 'beef_meatball_soup beef_noodle borscht braised_beef_with_potatoes brown_mushroom_pot_soup chicken_and_mushroom_stew crimson_fungus_pot_soup dough_drop_soup four_joy_meatball_soup hui_noodle laba_congee lamb_and_radish_soup pork_bone_soup pufferfish_soup red_mushroom_pot_soup seafood_miso_soup udon_noodle warped_fungus_pot_soup wild_mushroom_rabbit_soup', 'meal', 'substantial', 'protected_native_stockpot_keep_soup_base_and_seeded_flex',
       'Named current-source stockpot dishes preserve real soup base, counted components, native fixed/flexible semantics and actual portions; protected stirring plus native heat. Liquid default never fabricates unobserved water/yield/fluid accounting.', (4, 5, 'authored_liquid_soup_default_pending_composite_derivation'))
family('kc-native-thick-hot-composites', KC, 'fearsome_thick_soup hot_dry_noodles numbing_spicy_chicken spicy_blood_stew', 'meal', 'substantial', 'protected_native_stockpot_actual_retention_required',
       'Exact thick/drier native stockpot dishes retain their original apparatus and components. Do not infer a full liquid-soup water dose; actual retention/yield and native quality must determine composite values.')
family('kc-native-elaborate-stockpot-feast', KC, 'buddha_jumps_over_the_wall', 'meal', 'elaborate', 'protected_native_stockpot_keep_seeded_flex',
       'Pinned multi-component feast soup uses elaborate active preparation plus original native stockpot finish and real source-paid inputs. Native serving/yield and seeded flexible evaluator remain mandatory.', (4, 5, 'authored_liquid_soup_default_pending_composite_derivation'))
family('kc-native-stuffed-steamed-products', KC, 'bamboo_tube_rice baozi dumpling qingtuan shengjian_mantou zongzi', 'meal', 'substantial', 'protected_native_stuffed_or_steamed_chain',
       'Exact filled/steamed products pay source dough/rice/filling components and keep their original native stage/heat route. They are not equivalent plain dough/bread staples; stuffed raw intermediates, carriers and portion yields must be proven.')
family('kc-native-tea-egg-composite', KC, 'tea_egg', 'meal', 'straightforward', 'protected_native_stockpot_simple_flavored_egg',
       'Source stockpot pays actual cooked egg plus tea soup base: flavored prepared product rather than plain fried egg. Straightforward active preparation retains real soup-base volume and count-one-through-four recipe yields.')

# Explicit per-ID surface corrections from actual counted source recipes.
for names, adapter, reason in [
    ('bacon_and_eggs barbecue_stick grilled_salmon roasted_mutton_chops steak_and_potatoes stuffed_potato', 'native_served_plate_assembly_inherit', 'Pinned crafting inputs already pay cooked animal/grain/potato components; protect actual plating/assembly without inventing a second heat step.'),
    ('honey_glazed_ham_block roast_chicken_block stuffed_pumpkin_block', 'protected_roasting_heat_finish', 'Exact mixed/stuffed whole feast needs protected board formulation then real roasting, not name-derived pastry kneading.'),
    ('shepherds_pie_block', 'protected_board_layer_then_bake', 'Layer actual cooked mutton/potato/milk/onion components on the board, then bake the assembled feast; this is not raw flour dough kneading.'),
    ('rice_roll_medley_block', 'native_served_plate_assembly_inherit', 'Exact source assembles already prepared rolls and bowl; no extra oven or repeated full-feast work reward.'),
    ('glow_berry_custard', 'protected_mat_mix_then_steamer', 'Actual source cooking pays milk/egg/sugar/berries: mix custard then steam, not a cold uncooked-egg assembly.')]:
    for name in names.split():
        item = FD + ':' + name
        DOC['foods'][item] = {'adapter': adapter, 'reason': item + ': ' + reason,
            'evidence': [ref for row in FROZEN['recipes'].values() if row.get('raw') and any(out.get('id') == item for out in row['outputs']) for ref in row['evidence']]}

# Explicit authored raw-pastry chain decision: do not emit a false baked raw item.
RAW_PASTRIES = {UBE + ':' + name for name in 'ensaymada_raw ensaymada_ube_raw hopia_munggo_raw hopia_ube_raw pandesal_raw pandesal_ube_raw'.split()} | {KC + ':raw_meatball'}
for rid, row in sorted(FROZEN['recipes'].items()):
    raw_outputs = {out.get('id') for out in row['outputs']} & RAW_PASTRIES
    raw_inputs = {edge.get('id') for group in row['input_groups'] for edge in group if edge.get('kind') == 'item'} & RAW_PASTRIES
    # A larger soup that USES a raw formed ingredient is still its own paid
    # composite recipe, not the standalone raw->plain-cooked forming/bake chain.
    if KC + ':raw_meatball' in raw_inputs and not any(out.get('id') == KC + ':cooked_meatball' for out in row['outputs']):
        raw_inputs.remove(KC + ':raw_meatball')
    if not row.get('raw') or not (raw_outputs or raw_inputs): continue
    DOC['routes'][rid] = {'source_type': row['type'], 'disposition': 'deferred',
        'adapter': 'protected_raw_mat_formulation_then_actual_native_baking_chain',
        'reviewed': ['source_function', 'adapter'], 'evidence': row['evidence'],
        'unresolved': ['raw_formed_product_chain_requires_registered_transactional_probability_preserving_adapter'],
        'original_counted_outputs': row['outputs'],
        'reason': f'{rid}: exact raw-enriched-pastry formulation/bake link. Preserve original count/probabilistic bonuses, raw-stage model and real furnace/smoker/campfire finish. Never bake into the raw item, silently discard bonuses, or double-charge the full preparation clock.'}

acquisition = ROOT / 'authored/crop_acquisition.json'
if acquisition.exists():
    DOC['acquisition'] = json.loads(acquisition.read_text()); pin(acquisition)
    pin(DOC['acquisition']['canonical_authority_source'])

# Pin the actual reviewed resources and native source inspection inputs.
for value in FROZEN.get('pinned_external_mods', {}).values(): pin(value['jar']); pin(value['descriptor'])
pin(Path('/home/dev/mod_source/better-cooking/IMPLEMENTATION.md'))

# Bind every actually referenced KC food/acquisition/utility source, not just its filename.
pin('/home/dev/.tmp/kaleidoscope-review/kaleidoscopecookery-1.6.0-forge+mc1.20.1.jar')
KC_JAVA = Path('/home/dev/.tmp/kaleidoscope-review/sources/com/github/ysbbbbbb/kaleidoscopecookery')
for name in ['init/ModFoods.java', 'init/ModItems.java', 'init/registry/FoodBiteRegistry.java', 'block/crop/BaseCropBlock.java',
             'block/crop/RiceCropBlock.java', 'block/misc/StrawBlocks.java', 'event/ModTradesEvent.java',
             'effect/BaseEffect.java', 'effect/FlatulenceEffect.java', 'event/effect/FlatulenceEvent.java',
             'network/message/SimpleC2SModMessage.java', 'item/quality/QualityEvaluator.java']:
    pin(KC_JAVA / name)
DOC['foods'][KC + ':fried_caterpillar'] = {'reason': 'Source-reviewed renewable mobility utility, not plain fried animal food: +.75Y per shift press during guaranteed200tick beneficial flatulence. Protect native pot, preserve its evaluator/counts and enforce benefit bounds.',
    'evidence': [str(KC_JAVA / path) for path in ['init/ModFoods.java#FRIED_CATERPILLAR_ITEM', 'effect/BaseEffect.java', 'effect/FlatulenceEffect.java', 'event/effect/FlatulenceEvent.java']]}

# Reassemble already baked slices without rebaking or re-earning full dish work.
for rid in ['farmersdelight:apple_pie_from_slices', 'farmersdelight:chocolate_pie_from_slices',
            'farmersdelight:sweet_berry_cheesecake_from_slices', 'farmersdelight:pumpkin_pie_from_slices',
            'farmersdelight:cake_from_slices', 'ubesdelight:minecraft/crafting/ube_cake_from_ube_cake_slice']:
    row = FROZEN['recipes'][rid]
    if not row.get('raw'): raise ValueError('slice reassembly source resource missing: ' + rid)
    DOC['routes'][rid] = {'source_type': row['type'], 'disposition': 'deferred', 'surface': 'board',
        'work_class': 'none', 'action': 'assemble', 'adapter': 'native_paid_reassembly',
        'unresolved': ['paid_slice_reassembly_requires_native_age_quality_transfer_without_added_work'],
        'reviewed': ['source_function', 'surface', 'work_class', 'adapter'], 'evidence': row['evidence'],
        'reason': rid + ': exact recipe pays already baked/served slices. Retain actual native assembly/counts and transfer oldest age/worst quality and actual component nutrients; no second bake, reroll, or new minimum protected work budget. Native multi-slot paid-reassembly transfer adapter is still required.'}

# Exactly reviewed food-cut functions, not blanket "edible => protected meal".
# No new recipe discovered outside this finite list is automatically approved.
CUT_GROUPS = [
    ('farmersdelight:cutting/', 'beef chicken cod ham mutton porkchop salmon cooked_chicken cooked_cod cooked_mutton cooked_salmon smoked_ham', 'native_animal_staple_cut', 'Plain raw/cooked animal ingredient processing stays native and automatable; retain all bone/leather/byproduct probabilities without synthetic protected-meal labour.'),
    ('farmersdelight:cutting/', 'cabbage melon pumpkin tag_dough', 'native_ingredient_cut', 'Native vegetable/fruit/basic-pasta ingredient processing stays useful and automatable; actual one-knife completion and original quantities remain, not a 30s/eight work token.'),
    ('farmersdelight:cutting/', 'apple_pie cake chocolate_pie kelp_roll pumpkin_pie sweet_berry_cheesecake', 'paid_prepared_product_portion_cut', 'Slice an already prepared/baked product: transfer paid quality/oldest age and per-portion provenance without reroll, second division or a new minimum work budget. New whole-product production must pay upstream work.'),
    ('farmersdelight:cutting/', 'wild_carrots wild_onions wild_potatoes wild_tomatoes', 'finite_wild_forage_ingredient_cut', 'Native finite-wild ingredient recovery is not cold-meal assembly. Preserve exact food/dye/seed results subject to existing canonical propagation/origin authority; do not invent labour to hide acquisition closure.'),
    ('ubesdelight:farmersdelight/cutting/', 'garlic ginger compat/ginger_snowyspirit_compat', 'native_aromatic_ingredient_cut', 'Native aromatic ingredient cuts remain automatable with exact tool/amounts; optional compatibility conditions still need actual live resolution.'),
    ('ubesdelight:farmersdelight/cutting/', 'garlic_from_wild_garlic_from_cutting ginger_from_wild_ginger_from_cutting lemongrass_from_wild_lemongrass_from_cutting ube_from_wild_ube_from_cutting', 'finite_wild_forage_ingredient_cut', 'Exact wild-root/herb recovery retains all primary/dye results and canonical crop authority, not a protected manufactured meal.'),
    ('ubesdelight:farmersdelight/cutting/', 'halo_halo_from_halo_halo_feast_from_cutting leche_flan_from_leche_flan_feast_from_cutting ube_cake_slice_from_ube_cake_from_cutting', 'paid_prepared_product_portion_cut', 'Transfer already-paid feast/cake portions, preserving original counts, age and inherited quality without a second minimum protected work budget or nutrient division.')]
for prefix, names, function, reason in CUT_GROUPS:
    for name in names.split():
        rid = prefix + name; row = FROZEN['recipes'][rid]
        if row['type'] != 'farmersdelight:cutting' or not row.get('raw') or len(row['input_groups']) != 1:
            raise ValueError('reviewed single-input native cut source changed: ' + rid)
        DOC['routes'][rid] = {'source_type': 'farmersdelight:cutting', 'disposition': 'retain_bulk', 'surface': 'board',
            'work_class': 'none', 'action': 'cut', 'adapter': 'native_bulk_cut', 'cut_function': function,
            'native_automation': 'allowed_ingredient_processing_or_paid_portioning', 'native_cut_count': 1,
            'serializer_contract_status': 'root_approved_pending_native',
            'unresolved': ['native_cutting_chances_age_quality_and_native_collection_require_acceptance'],
            'reviewed': ['source_function', 'surface', 'work_class', 'adapter'], 'evidence': row['evidence'],
            'reason': rid + ': ' + reason + ' Preserve the exact original recipe ID, Forge tool, input alternatives and every ordered counted/chance output on canonical KC board.'}

# This one explicit approved document is replayed, never draft-directory discovery.
# Drafts remain excluded from actual policy/runtime source authority.
APPROVED_FAMILIES = ROOT / 'authored/family-reviews.json'
if APPROVED_FAMILIES.exists():
    from merge_family_reviews import merge_family_reviews
    DOC = merge_family_reviews(DOC, FROZEN, APPROVED_FAMILIES, LOCK)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'authored/decisions.json')
    output = parser.parse_args().output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(DOC, indent=2, sort_keys=True) + '\n')
    ids = {i for family in DOC['families'] for i in family['items']} | set(DOC['foods'])
    print(f'authored {len(ids)} exact food decisions, {len(DOC["routes"])} exact route decisions; no native qualification')
