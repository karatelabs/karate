/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.robot.win;

import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.ITypeInfo;
import com.sun.jna.platform.win32.COM.TypeInfo;
import com.sun.jna.platform.win32.COM.TypeLib;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.OaIdl;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ComLibrary {

    private static final Logger logger = LoggerFactory.getLogger(ComLibrary.class);      

    public final String name;
    public final String clsId;
    public final int majorVersion;
    public final int minorVersion;
    public final Map<String, Map<String, Integer>> enumKeyValues = new HashMap();
    public final Map<String, Map<Integer, String>> enumValueKeys = new HashMap();
    public final Map<String, ComInterface> interfaces = new HashMap();

    public ComLibrary(String typeLibClsId, int majorVersion, int minorVersion) {
        // load registered class id
        WinDef.LCID lcId = Kernel32.INSTANCE.GetUserDefaultLCID();
        Guid.CLSID.ByReference clsIdRef = new Guid.CLSID.ByReference();
        WinNT.HRESULT hr = Ole32.INSTANCE.CLSIDFromString(typeLibClsId, clsIdRef);
        COMUtils.checkRC(hr);

        // load type library
        PointerByReference typeLibRef = new PointerByReference();
        hr = OleAuto.INSTANCE.LoadRegTypeLib(clsIdRef, majorVersion, minorVersion, lcId, typeLibRef);
        COMUtils.checkRC(hr);

        TypeLib typeLib = new TypeLib(typeLibRef.getValue());
        name = getName(typeLib, -1);
        clsId = clsIdRef.toGuidString();
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        logger.debug("loaded: {}, clsid: {}, majorVersion: {}, minorVersion: {}",
                name, clsId, majorVersion, minorVersion);

        int typeCount = typeLib.GetTypeInfoCount().intValue();
        if (logger.isTraceEnabled()) {
            logger.trace("name: {}, types: {}", name, typeCount);
        }
        for (int i = 0; i < typeCount; i++) {
            String typeName = getName(typeLib, i);
            ITypeInfo typeInfo = getTypeInfo(typeLib, i);
            OaIdl.TYPEATTR typeAttr = getTypeAttr(typeInfo);
            String guid = typeAttr.guid.toGuidString();
            OaIdl.TYPEKIND typeKind = getTypeKind(typeLib, i);
            switch (typeKind.value) {
                case OaIdl.TYPEKIND.TKIND_ENUM:
                case OaIdl.TYPEKIND.ALIGN_GNUC: // UIA_PropertyIds etc.                    
                    getEnums(typeName, typeInfo, typeAttr);
                    break;
                case OaIdl.TYPEKIND.TKIND_INTERFACE:
                case OaIdl.TYPEKIND.TKIND_DISPATCH:
                case OaIdl.TYPEKIND.TKIND_COCLASS:
                    getInterfaces(guid, typeName, typeInfo, typeAttr);
                    break;
                default:
                    if (logger.isTraceEnabled()) {
                        logger.trace("==== ignore: {}", typeName);
                    }
            }
        }
    }

    //==========================================================================
    //
    private void getInterfaces(String guid, String interfaceName, ITypeInfo typeInfo, OaIdl.TYPEATTR typeAttr) {
        int implCount = typeAttr.cImplTypes.intValue();
        if (implCount > 0) {
            for (int i = 0; i < implCount; i++) {
                OaIdl.HREFTYPE refTypeOfImplType = getRefType(typeInfo, i);
                ITypeInfo refTypeInfo = getRefTypeInfo(typeInfo, refTypeOfImplType);
                String implementingName = getName(refTypeInfo, new OaIdl.MEMBERID(-1));
                ComInterface ci = new ComInterface(interfaceName, implementingName, guid);
                interfaces.put(interfaceName, ci);
                getFunctions(ci, typeInfo);
                if (logger.isTraceEnabled()) {
                    logger.trace("==== interface: {}", ci);
                }
            }
        }
    }

    private void getFunctions(ComInterface ci, ITypeInfo typeInfo) {
        OaIdl.TYPEATTR typeAttr = getTypeAttr(typeInfo);
        int count = typeAttr.cFuncs.intValue();
        for (int i = 0; i < count; i++) {
            OaIdl.FUNCDESC funcDesc = getFuncDesc(typeInfo, i);
            int paramCount = funcDesc.cParams.shortValue();
            int vtableId = funcDesc.oVft.intValue();
            int memberId = funcDesc.memid.intValue();
            String[] names = getNames(typeInfo, funcDesc.memid, paramCount + 1);
            String functionName = names[0];
            ComFunction cf = new ComFunction(functionName, vtableId, memberId);
            ci.add(cf);
            getArgs(cf, names, typeInfo, funcDesc);
        }
    }

    private void getArgs(ComFunction cf, String[] names, ITypeInfo typeInfo, OaIdl.FUNCDESC funcDesc) {
        for (int i = 1; i < names.length; i++) {
            OaIdl.ELEMDESC elemdesc = funcDesc.lprgelemdescParam.elemDescArg[i - 1];
            cf.addArg(names[i]);
        }
    }

    private static String[] getNames(ITypeInfo typeInfo, OaIdl.MEMBERID memberId, int maxNames) {
        WTypes.BSTR[] namesRef = new WTypes.BSTR[maxNames];
        WinDef.UINTByReference indexRef = new WinDef.UINTByReference();
        WinNT.HRESULT hr = typeInfo.GetNames(memberId, namesRef, new WinDef.UINT(maxNames), indexRef);
        COMUtils.checkRC(hr);
        int cNames = indexRef.getValue().intValue();
        String[] result = new String[cNames];
        for (int i = 0; i < result.length; i++) {
            result[i] = namesRef[i].getValue();
            OleAuto.INSTANCE.SysFreeString(namesRef[i]);
        }
        return result;
    }

    private static OaIdl.FUNCDESC getFuncDesc(ITypeInfo typeInfo, int index) {
        PointerByReference funcDescRef = new PointerByReference();
        WinNT.HRESULT hr = typeInfo.GetFuncDesc(new WinDef.UINT(index), funcDescRef);
        COMUtils.checkRC(hr);
        return new OaIdl.FUNCDESC(funcDescRef.getValue());
    }

    private static OaIdl.HREFTYPE getRefType(ITypeInfo typeInfo, int index) {
        OaIdl.HREFTYPEByReference refTypeRef = new OaIdl.HREFTYPEByReference();
        WinNT.HRESULT hr = typeInfo.GetRefTypeOfImplType(new WinDef.UINT(index), refTypeRef);
        COMUtils.checkRC(hr);
        return refTypeRef.getValue();
    }

    private static ITypeInfo getRefTypeInfo(ITypeInfo typeInfo, OaIdl.HREFTYPE hrefType) {
        PointerByReference refTypeInfoRef = new PointerByReference();
        WinNT.HRESULT hr = typeInfo.GetRefTypeInfo(hrefType, refTypeInfoRef);
        COMUtils.checkRC(hr);
        return new TypeInfo(refTypeInfoRef.getValue());
    }

    private void getEnums(String enumName, ITypeInfo typeInfo, OaIdl.TYPEATTR typeAttr) {
        int varCount = typeAttr.cVars.intValue();
        Map<String, Integer> keyValues = new LinkedHashMap();
        this.enumKeyValues.put(enumName, keyValues); 
        Map<Integer, String> valueKeys = new HashMap();
        this.enumValueKeys.put(enumName, valueKeys);        
        if (varCount > 0) {
            for (int i = 0; i < varCount; i++) {
                OaIdl.VARDESC varDesc = getVarDesc(typeInfo, i);
                Variant.VARIANT constValue = varDesc._vardesc.lpvarValue;
                Object value = constValue.getValue();
                OaIdl.MEMBERID memberId = varDesc.memid;
                String name = getName(typeInfo, memberId);
                Integer intValue = Integer.valueOf(value.toString());
                keyValues.put(name, intValue);
                valueKeys.put(intValue, name);
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("enum: {} - {}", enumName, keyValues);
        }
    }

    private static OaIdl.VARDESC getVarDesc(ITypeInfo typeInfo, int index) {
        PointerByReference varDescRef = new PointerByReference();
        WinNT.HRESULT hr = typeInfo.GetVarDesc(new WinDef.UINT(index), varDescRef);
        COMUtils.checkRC(hr);
        return new OaIdl.VARDESC(varDescRef.getValue());
    }

    private static String getName(TypeLib typeLib, int index) {
        WTypes.BSTRByReference nameRef = new WTypes.BSTRByReference();
        WTypes.BSTRByReference docRef = new WTypes.BSTRByReference();
        WinDef.DWORDByReference helpRef = new WinDef.DWORDByReference();
        WTypes.BSTRByReference helpFileRef = new WTypes.BSTRByReference();
        WinNT.HRESULT hr = typeLib.GetDocumentation(index, nameRef, docRef, helpRef, helpFileRef);
        COMUtils.checkRC(hr);
        String name = nameRef.getString();
        OleAuto.INSTANCE.SysFreeString(nameRef.getValue());
        OleAuto.INSTANCE.SysFreeString(docRef.getValue());
        OleAuto.INSTANCE.SysFreeString(helpFileRef.getValue());
        return name;
    }

    private static String getName(ITypeInfo typeInfo, OaIdl.MEMBERID memberId) {
        WTypes.BSTRByReference nameRef = new WTypes.BSTRByReference();
        WTypes.BSTRByReference docRef = new WTypes.BSTRByReference();
        WinDef.DWORDByReference helpRef = new WinDef.DWORDByReference();
        WTypes.BSTRByReference helpFileRef = new WTypes.BSTRByReference();
        WinNT.HRESULT hr = typeInfo.GetDocumentation(memberId, nameRef, docRef, helpRef, helpFileRef);
        COMUtils.checkRC(hr);
        String name = nameRef.getString();
        OleAuto.INSTANCE.SysFreeString(nameRef.getValue());
        OleAuto.INSTANCE.SysFreeString(docRef.getValue());
        OleAuto.INSTANCE.SysFreeString(helpFileRef.getValue());
        return name;
    }

    private static OaIdl.TYPEKIND getTypeKind(TypeLib typeLib, int index) {
        OaIdl.TYPEKIND.ByReference typeKind = new OaIdl.TYPEKIND.ByReference();
        WinNT.HRESULT hr = typeLib.GetTypeInfoType(new WinDef.UINT(index), typeKind);
        COMUtils.checkRC(hr);
        return typeKind;
    }

    private static ITypeInfo getTypeInfo(TypeLib typeLib, int index) {
        PointerByReference typeInfoRef = new PointerByReference();
        WinNT.HRESULT hr = typeLib.GetTypeInfo(new WinDef.UINT(index), typeInfoRef);
        COMUtils.checkRC(hr);
        return new TypeInfo(typeInfoRef.getValue());
    }

    private static OaIdl.TYPEATTR getTypeAttr(ITypeInfo typeInfo) {
        PointerByReference typeAttrRef = new PointerByReference();
        WinNT.HRESULT hr = typeInfo.GetTypeAttr(typeAttrRef);
        COMUtils.checkRC(hr);
        return new OaIdl.TYPEATTR(typeAttrRef.getValue());
    }

}
