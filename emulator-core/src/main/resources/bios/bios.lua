-- OpenComputersAndroid demo BIOS.
-- Runs from the emulated EEPROM when a machine powers on: binds the GPU to the
-- first screen, prints a boot banner and component inventory, then echoes
-- keyboard input to the display until the machine shuts down.

local gpuAddr = component.list("gpu")()
local screenAddr = component.list("screen")()
assert(gpuAddr, "no gpu component")
assert(screenAddr, "no screen component")

local gpu = component.proxy(gpuAddr)
gpu.bind(screenAddr)

local maxW, maxH = gpu.maxResolution()
local w = math.min(maxW, 80)
local h = math.min(maxH, 25)
gpu.setResolution(w, h)

gpu.setBackground(0x00213D)
gpu.setForeground(0xFFFFFF)
gpu.fill(1, 1, w, h, " ")

local row = 1
local function printLine(text)
  if row <= h then
    gpu.set(2, row, text)
    row = row + 1
  end
end

printLine("OpenComputersAndroid")
printLine("architecture: " .. tostring(computer.getArchitecture()))
printLine("machine:      " .. computer.address():sub(1, 13) .. "...")
printLine("")
printLine("components:")
for addr, ctype in component.list() do
  printLine("  " .. ctype .. "  " .. addr:sub(1, 8))
end
printLine("")
printLine("type on the keyboard to echo input:")

local echoX = 2
local echoRow = row + 1

while true do
  local sig = {computer.pullSignal()}
  local name = sig[1]
  if name == "key_down" then
    local char = sig[3]
    if char >= 32 and char < 127 then
      gpu.set(echoX, echoRow, string.char(char))
      echoX = echoX + 1
      if echoX > w - 1 then
        echoX = 2
        echoRow = echoRow + 1
        if echoRow > h then echoRow = row + 1 end
      end
    elseif char == 8 then -- backspace
      if echoX > 2 then
        echoX = echoX - 1
        gpu.set(echoX, echoRow, " ")
      end
    elseif char == 10 or char == 13 then -- enter
      echoX = 2
      echoRow = echoRow + 1
      if echoRow > h then echoRow = row + 1 end
    end
  end
end
