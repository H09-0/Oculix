# Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
# Python 3 / Jython 3 core Sikuli/OculiX module

import time
import sys
import os

from org.sikuli.basics import Debug, Settings, OS
import org.sikuli.script.SX as SXclass
import org.sikuli.support.Commons as JCommons

from org.sikuli.script import FindFailed, FindFailedResponse, Constants, Button
from org.sikuli.script import Region, Screen, Location, Offset, Finder
from org.sikuli.script import TextRecognizer as TextOCR, OCR, Match, ImagePath
from org.sikuli.script import Pattern, Image, App, Key, KeyModifier, Mouse, Env
from org.sikuli.script.Constants import *
from org.sikuli.script.FindFailedResponse import *
from org.sikuli.script.Button import WHEEL_UP, WHEEL_DOWN
from org.sikuli.script.KeyModifier import KEY_CTRL, KEY_SHIFT, KEY_META, KEY_CMD, KEY_WIN, KEY_ALT
from org.sikuli.script.compare import DistanceComparator, VerticalComparator, HorizontalComparator
import org.sikuli.support.runner.Runner as Runner
import org.sikuli.support.runnerSupport.JythonSupport as JythonHelper
import org.sikuli.support.devices.ScreenDevice as SD

Debug.log(3, "Jython3: starting sikuli init")

ALL = Screen.getPrimaryScreen()
SCREEN = SD.getPrimaryScreen()
remoteScreen = None


def reset():
    global ALL, SCREEN
    Screen.resetMonitors()
    ALL = Screen.getPrimaryScreen().getRegion()
    SCREEN = SD.getPrimaryScreen()


def resetBeforeScriptStart():
    global SCREEN
    scr = SD.getPrimaryScreen()
    scr.reset()
    SCREEN = scr


def use(scr=None, remote=False, fromWith=False):
    global SCREEN, remoteScreen
    if remoteScreen is not None:
        remoteScreen.close()
        remoteScreen = None
    if scr is None:
        newScreen = SD.getPrimaryScreen()
    else:
        newScreen = scr
    SCREEN = newScreen
    if remote:
        remoteScreen = SCREEN
    return SCREEN


def useRemote(adr, port=0):
    screen = SD.getRemoteScreen(adr, str(port))
    if screen is not None:
        return use(screen, True)
    return None


# ── Screen-delegated visual automation functions ──

def find(*args):
    return SCREEN.find(*args)


def findAll(*args):
    return SCREEN.findAll(*args)


def findBest(*args):
    return SCREEN.findBest(*args)


def findAny(*args):
    return SCREEN.findAny(*args)


def findText(*args):
    return SCREEN.findText(*args)


def exists(*args):
    return SCREEN.exists(*args)


def wait(target, timeout=None):
    if isinstance(target, (int, float)) and timeout is None:
        time.sleep(float(target))
        return None
    if timeout is None:
        return SCREEN.wait(target)
    return SCREEN.wait(target, float(timeout))


def waitVanish(*args):
    return SCREEN.waitVanish(*args)


def click(*args):
    return SCREEN.click(*args)


def doubleClick(*args):
    return SCREEN.doubleClick(*args)


def rightClick(*args):
    return SCREEN.rightClick(*args)


def hover(*args):
    return SCREEN.hover(*args)


def dragDrop(*args):
    return SCREEN.dragDrop(*args)


def drag(*args):
    return SCREEN.drag(*args)


def dropAt(*args):
    return SCREEN.dropAt(*args)


def type(*args):
    return SCREEN.type(*args)


def write(*args):
    return SCREEN.write(*args)


def paste(*args):
    return SCREEN.paste(*args)


def wheel(*args):
    return SCREEN.wheel(*args)


def mouseMove(*args):
    return SCREEN.mouseMove(*args)


def mouseDown(*args):
    return SCREEN.mouseDown(*args)


def mouseUp(*args):
    return SCREEN.mouseUp(*args)


def keyDown(*args):
    return SCREEN.keyDown(*args)


def keyUp(*args):
    return SCREEN.keyUp(*args)


def highlight(*args):
    return SCREEN.highlight(*args)


def capture(*args):
    scrImg = SCREEN.cmdCapture(args)
    if scrImg is not None:
        return scrImg.getStoredAt()
    return None


def at():
    return Mouse.at()


# ── Extension / utility functions ──

def load(jar):
    return JythonHelper.get().load(jar)


def prepareRobot():
    return JythonHelper.get().prepareRobot()


def show():
    JCommons.show()


def uprint(*args):
    print(*args)


def unicd(s):
    return str(s)


def ucode(s):
    return str(s)


def unzip(zip_path, target):
    import org.sikuli.support.FileManager as FM
    return FM.unzip(str(zip_path), str(target))


def _addModPath(path):
    sep = Settings.getFilePathSeperator()
    if path.endswith(sep):
        path = path[:-1]
    JythonHelper.get().appendSysPath(path)


def addImportPath(path):
    _addModPath(path)


def addImagePath(*args):
    if len(args) == 1:
        return ImagePath.append(args[0])
    elif len(args) == 2:
        return ImagePath.append(args[0], args[1])
    return None


def addHTTPImagePath(arg):
    return ImagePath.addHTTP(arg)


def addJarImagePath(*args):
    if len(args) == 1:
        return ImagePath.addJar(args[0])
    elif len(args) == 2:
        return ImagePath.addJar(args[0], args[1])
    return None


def removeImagePath(*args):
    if len(args) == 1:
        return ImagePath.remove(args[0])
    elif len(args) == 2:
        return ImagePath.remove(args[0], args[1])
    return None


def removeHTTPImagePath(arg):
    return ImagePath.removeHTTP(arg)


def removeJarImagePath(*args):
    if len(args) == 1:
        return ImagePath.removeJar(args[0])
    elif len(args) == 2:
        return ImagePath.removeJar(args[0], args[1])
    return None


def getImagePath():
    return [e for e in ImagePath.get()]


def resetImagePath(path=None):
    ImagePath.reset(path)


def checkImage(image):
    return ImagePath.check(image)


def setBundlePath(path=None):
    return ImagePath.setBundlePath(path)


def getBundlePath():
    return ImagePath.getBundlePath()


def getBundleFolder():
    path = ImagePath.getBundlePath()
    if not path:
        return None
    return path + Settings.getFilePathSeperator()


def getParentPath():
    path = ImagePath.getBundlePath()
    if not path:
        return None
    return os.path.dirname(makePath(getBundlePath()))


def getParentFolder():
    path = getParentPath()
    if not path:
        return None
    return path + Settings.getFilePathSeperator()


def makePath(*paths):
    if len(paths) == 0:
        return None
    path = paths[0]
    if len(paths) > 1:
        for p in paths[1:]:
            path = os.path.join(path, p)
    sep = Settings.getFilePathSeperator()
    if path.endswith(sep):
        return os.path.dirname(path)
    return path


def makeFolder(*paths):
    path = makePath(*paths)
    if not path:
        return None
    return path + Settings.getFilePathSeperator()


def setShowActions(flag):
    Settings.setShowActions(flag)


def popat(*args):
    if len(args) == 0:
        return SXclass.popat()
    elif len(args) > 1:
        return SXclass.popat(args[0], args[1])
    return SXclass.popat(args[0])


def popup(msg, title="OculiX Info"):
    SXclass.popup(msg, title)


def popError(msg, title="OculiX Error"):
    SXclass.popError(msg, title)


def popAsk(msg, title="OculiX Decision"):
    return SXclass.popAsk(msg, title)


def input(msg="", default="", title="OculiX Input", hidden=False):
    if hidden:
        default = ""
    return SXclass.input(msg, title, default, hidden)


def inputText(msg="", title="", lines=0, width=0, text=""):
    return SXclass.inputText(msg, title, lines, width, text)


def select(msg="", title="OculiX Selection", options=(), default=None):
    optionsLen = len(options)
    if optionsLen == 0:
        return ""
    if isinstance(default, int) and 0 <= default < optionsLen:
        default = options[default]
    optionString = ""
    for option in options:
        optionString += str(len(option) + 1000) + option
    return SXclass.popSelect(msg, title, default, optionString)


def popFile(title="Select File or Folder"):
    return SXclass.popFile("", title)


def switchApp(app):
    return App.open(app)


def openApp(app):
    return App.open(app)


def closeApp(app):
    return App.close(app)


def sleep(sec):
    time.sleep(sec)


def exit(code=0):
    global remoteScreen
    if remoteScreen is not None:
        remoteScreen.close()
        remoteScreen = None
    sys.exit(code)


def run(cmd):
    return JCommons.run(cmd)


def runScript(script, *args):
    return Runner.runScript(str(script), list(args), None)


def getLastReturnCode():
    return Runner.getLastScriptRunReturnCode()


def byDistanceTo(x, y=None):
    return DistanceComparator(x, y)


def byX(m):
    return HorizontalComparator().compare


def byY(m):
    return VerticalComparator().compare


def verticalComparator():
    return VerticalComparator().compare


def horizontalComparator():
    return HorizontalComparator().compare


def distanceComparator(x, y=None):
    if y is None:
        return DistanceComparator(x).compare
    return DistanceComparator(x, y).compare


Debug.log(3, "Jython3: ending sikuli init")
