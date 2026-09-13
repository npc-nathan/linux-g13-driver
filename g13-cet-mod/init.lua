-- g13-hud: Cyberpunk 2077's own numbers on a Logitech G13 screen.
--
-- This writes one small JSON file, about five times a second, and does nothing else. The G13
-- side reads it with the driver's `json:` data source, so the screen is designed and drawn on
-- the Linux side: this file is data, not a picture. Run G13Probe() in the CET console to see
-- what this particular game build offers.
--
-- Verified against mods that already run in this game (the calls are theirs):
--   health/stamina/level   Game.GetStatsSystem():GetStatValue(entityId, 'Health')
--   tracked objective      Game.GetJournalManager():GetTrackedEntry()
--   player position        Game.GetPlayer():GetWorldPosition() / GetWorldForward()
--   loc keys to text       GetLocalizedText(key)
-- Unverified calls are tried defensively and simply come back empty: the game build decides.
--
-- Requires CET 1.37+ (written against 1.37.1). Install to
--   <game>/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud/init.lua

local UPDATE_SECONDS = 0.2
local OUTPUT_FILE = "hud.json"      -- inside this mod's folder, which is all the sandbox allows
local VERSION = "1.0"

local elapsed = 0

local function number_from(fn)
    local ok, value = pcall(fn)
    if ok and type(value) == "number" and value == value then   -- reject NaN
        return value
    end
    return nil
end

-- A loc key to display text, or the key itself if it is not one.
local function localized(value)
    if type(value) ~= "string" or value == "" then
        return nil
    end
    local ok, text = pcall(GetLocalizedText, value)
    if ok and type(text) == "string" and text ~= "" then
        return text
    end
    return value
end

local function entry_text(entry)
    if not entry then
        return nil
    end
    for _, method in ipairs({ "GetTitle", "GetDescription" }) do
        local ok, value = pcall(function() return entry[method](entry) end)
        if ok then
            local text = localized(value)
            if text then
                return text
            end
        end
    end
    return nil
end

local function tracked()
    local state = {}
    local ok, entry = pcall(function() return Game.GetJournalManager():GetTrackedEntry() end)
    if not ok or not entry then
        return state
    end

    local quest
    pcall(function()
        local phase = Game.GetJournalManager():GetParentEntry(entry)
        if phase then
            quest = Game.GetJournalManager():GetParentEntry(phase)
        end
    end)

    state.objective = entry_text(entry)
    state.quest = entry_text(quest)
    pcall(function() state.objective_id = tostring(entry.id) end)
    pcall(function() state.quest_id = quest and tostring(quest.id) end)
    return state
end

local function weapon()
    local state = {}
    local ok, held = pcall(function() return Game.GetPlayer():GetActiveWeapon() end)
    if not ok or not held then
        return state
    end

    pcall(function() state.weapon = localized(held:GetName()) end)

    -- Which of these a build answers varies; whichever does is used.
    local candidates = {
        ammo = "GetMagazineAmmoCount",
        ammo_total = "GetAmmoCount",
        ammo_max = "GetMaxAmmoCount",
    }
    for key, method in pairs(candidates) do
        local value = number_from(function() return held[method](held) end)
        if value then
            state[key] = value
        end
    end
    return state
end

local function collect()
    local state = {
        mod = "g13-hud",
        version = VERSION,
        updated = os.date("%H:%M:%S"),
        health = number_from(function()
            return Game.GetStatsSystem():GetStatValue(Game.GetPlayer():GetEntityID(), "Health")
        end),
        stamina = number_from(function()
            return Game.GetStatsSystem():GetStatValue(Game.GetPlayer():GetEntityID(), "Stamina")
        end),
        level = number_from(function()
            return Game.GetStatsSystem():GetStatValue(Game.GetPlayer():GetEntityID(), "Level")
        end),
        streetcred = number_from(function()
            return Game.GetStatsSystem():GetStatValue(Game.GetPlayer():GetEntityID(), "StreetCred")
        end),
    }

    local player = Game.GetPlayer()
    local position
    pcall(function() position = player:GetWorldPosition() end)
    if position then
        state.x, state.y, state.z = position.x, position.y, position.z
    end

    -- Heading, in degrees, 0 = north and 90 = east, which is what the arrow expects. The
    -- axis convention is worth a glance in game; it is one line to flip if it reads backwards.
    local forward
    pcall(function() forward = player:GetWorldForward() end)
    if forward then
        state.heading = math.floor((math.deg(math.atan(forward.x, forward.y)) + 360) % 360)
    end

    for key, value in pairs(tracked()) do
        state[key] = value
    end
    for key, value in pairs(weapon()) do
        state[key] = value
    end
    return state
end

local function write(state)
    local ok, text = pcall(json.encode, state)
    if not ok or type(text) ~= "string" then
        return
    end

    -- Written beside the real file and renamed over it, so the reader never sees half of it.
    -- If the rename is not allowed, a direct write is good enough: the reader ignores
    -- unreadable JSON rather than failing.
    local renamed = pcall(function()
        local file = io.open(OUTPUT_FILE .. ".tmp", "w")
        if not file then
            return false
        end
        file:write(text)
        file:close()
        os.remove(OUTPUT_FILE)
        os.rename(OUTPUT_FILE .. ".tmp", OUTPUT_FILE)
        return true
    end)

    if not renamed then
        pcall(function()
            local file = io.open(OUTPUT_FILE, "w")
            if file then
                file:write(text)
                file:close()
            end
        end)
    end
end

-- A console helper: G13Probe() lists what this build answers, so a missing field can be
-- tracked down in one session rather than guessed at.
function G13Probe()
    print("[g13-hud] probe, version " .. VERSION)
    local player = Game.GetPlayer()
    local methods = {
        "GetActiveWeapon", "GetWorldPosition", "GetWorldForward", "GetQuickSlotsManager",
    }
    for _, method in ipairs(methods) do
        local ok, value = pcall(function() return player[method](player) end)
        print(string.format("  player:%s -> %s", method, ok and tostring(value) or "refused"))
    end

    local held
    pcall(function() held = player:GetActiveWeapon() end)
    if held then
        for _, method in ipairs({ "GetName", "GetMagazineAmmoCount", "GetAmmoCount",
                                  "GetMaxAmmoCount", "GetAmmoLeftCount" }) do
            local ok, value = pcall(function() return held[method](held) end)
            print(string.format("  weapon:%s -> %s", method, ok and tostring(value) or "refused"))
        end
    end

    for _, name in ipairs({ "Health", "Stamina", "Level", "StreetCred" }) do
        local value = number_from(function()
            return Game.GetStatsSystem():GetStatValue(player:GetEntityID(), name)
        end)
        print(string.format("  stat %-12s -> %s", name, tostring(value)))
    end

    local entry
    pcall(function() entry = Game.GetJournalManager():GetTrackedEntry() end)
    print("  tracked entry -> " .. tostring(entry ~= nil))
    if entry then
        for _, method in ipairs({ "GetTitle", "GetDescription", "GetMappinPath", "GetPosition" }) do
            local ok, value = pcall(function() return entry[method](entry) end)
            print(string.format("  entry:%s -> %s", method, ok and tostring(value) or "refused"))
        end
    end
    print("[g13-hud] end of probe")
end

registerForEvent("onInit", function()
    print(string.format("[g13-hud] v%s: writing %s every %.2fs", VERSION, OUTPUT_FILE,
                        UPDATE_SECONDS))
    write(collect())
end)

registerForEvent("onUpdate", function(delta)
    elapsed = elapsed + (delta or 0)
    if elapsed < UPDATE_SECONDS then
        return
    end
    elapsed = 0
    write(collect())
end)
