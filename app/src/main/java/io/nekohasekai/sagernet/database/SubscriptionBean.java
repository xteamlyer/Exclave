/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.database;

import android.os.Build;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.SubscriptionType;
import io.nekohasekai.sagernet.fmt.Serializable;
import io.nekohasekai.sagernet.ktx.KryosKt;

public class SubscriptionBean extends Serializable {

    public Integer type;
    public String link;
    public Boolean deduplication;
    public Boolean updateWhenConnectedOnly;
    public String customUserAgent;
    public Boolean autoUpdate;
    public Integer autoUpdateDelay;
    public Long lastUpdated;
    public Long bytesUsed;
    public Long bytesRemaining;
    public Long expiryDate;

    public String nameFilter;
    public String nameFilter1;
    public Boolean importRoutingRules;
    public Boolean happSpoof;
    public String happAppVersion;
    public String happOs;
    public String happOsVersion;
    public String happDeviceModel;
    public String happLocale;
    public String happUserId;
    public String happHwid;
    public String httpHeaders;
    public String agePrivateKey;

    public SubscriptionBean() {
    }

    @Override
    public void serializeToBuffer(ByteBufferOutput output) {
        output.writeInt(12);
        output.writeInt(type);
        output.writeString(link);
        output.writeBoolean(deduplication);
        output.writeBoolean(updateWhenConnectedOnly);
        output.writeString(customUserAgent);
        output.writeBoolean(autoUpdate);
        output.writeInt(autoUpdateDelay);
        output.writeLong(lastUpdated);
        output.writeLong(bytesUsed);
        output.writeLong(bytesRemaining);
        output.writeLong(expiryDate);
        output.writeString(nameFilter);
        output.writeString(nameFilter1);
        output.writeString(httpHeaders);
        output.writeString(agePrivateKey);
        output.writeBoolean(importRoutingRules);
        output.writeBoolean(happSpoof);
        output.writeString(happAppVersion);
        output.writeString(happOs);
        output.writeString(happOsVersion);
        output.writeString(happDeviceModel);
        output.writeString(happLocale);
        output.writeString(happUserId);
        output.writeString(happHwid);
    }

    public void serializeForShare(ByteBufferOutput output) {
        output.writeInt(8);
        output.writeInt(type);
        output.writeString(link);
        output.writeBoolean(deduplication);
        output.writeBoolean(updateWhenConnectedOnly);
        output.writeString(customUserAgent);
        output.writeLong(bytesUsed);
        output.writeLong(bytesRemaining);
        output.writeLong(expiryDate);
        output.writeString(nameFilter);
        output.writeString(nameFilter1);
        output.writeString(httpHeaders);
        output.writeString(agePrivateKey);
    }

    @Override
    public void deserializeFromBuffer(ByteBufferInput input) {
        int version = input.readInt();

        type = input.readInt();

        if (version < 7 && type == SubscriptionType.OOCv1) {
            input.readString(); // token, removed
            link = "";
        } else {
            link = input.readString();
        }
        if (version < 6) {
            input.readBoolean(); // forceResolve, removed
        }

        deduplication = input.readBoolean();
        if (version < 2) input.readBoolean();
        updateWhenConnectedOnly = input.readBoolean();
        customUserAgent = input.readString();
        autoUpdate = input.readBoolean();
        autoUpdateDelay = input.readInt();
        if (version <= 3) {
            lastUpdated = (long) input.readInt();
        } else {
            lastUpdated = input.readLong();
        }


        if (type == SubscriptionType.RAW && version == 3) {
            input.readString(); // subscriptionUserinfo, removed
        }

        if (type != SubscriptionType.RAW || version >= 4) {
            bytesUsed = input.readLong();
            bytesRemaining = input.readLong();
        }

        if (version >= 4) {
            expiryDate = input.readLong();
        }

        if (version >= 5) {
            nameFilter = input.readString();
        }

        if (version < 7 && type == SubscriptionType.OOCv1) {
            input.readString();
            if (version <= 3) {
                input.readInt();
            }
            KryosKt.readStringList(input);
            if (input.canReadVarInt()) {
                KryosKt.readStringSet(input);
                if (version >= 1) {
                    KryosKt.readStringSet(input);
                }
                KryosKt.readStringSet(input);
            }
        }

        if (version >= 8) {
            nameFilter1 = input.readString();
        }

        if (version >= 12) {
            // combined fork+upstream layout
            httpHeaders = input.readString();
            String s = input.readString();
            if (type == SubscriptionType.AGE) {
                agePrivateKey = s;
            } else {
                agePrivateKey = "";
            }
            importRoutingRules = input.readBoolean();
            happSpoof = input.readBoolean();
            happAppVersion = input.readString();
            happOs = input.readString();
            happOsVersion = input.readString();
            happDeviceModel = input.readString();
            happLocale = input.readString();
            happUserId = input.readString();
            happHwid = input.readString();
        } else if (version >= 9) {
            // Version 9-11 is ambiguous: BetterExclave (before the 0.17.46 merge) wrote the
            // happ fields here, while stock Exclave 0.17.46+ wrote httpHeaders/agePrivateKey
            // strings at version 9. Try the fork layout first and fall back to the stock one.
            int legacyPosition = input.position();
            boolean forkLayout;
            try {
                if (version >= 11) {
                    importRoutingRules = input.readBoolean();
                }
                happSpoof = input.readBoolean();
                if (version >= 10) {
                    happAppVersion = input.readString();
                    happOs = input.readString();
                    happOsVersion = input.readString();
                    happDeviceModel = input.readString();
                    happLocale = input.readString();
                    happUserId = input.readString();
                    happHwid = input.readString();
                }
                forkLayout = version < 10 || looksSane(happOs) && looksSane(happLocale) && looksSane(happAppVersion);
            } catch (Exception e) {
                forkLayout = false;
            }
            if (!forkLayout) {
                importRoutingRules = false;
                happSpoof = false;
                happAppVersion = null;
                happOs = null;
                happOsVersion = null;
                happDeviceModel = null;
                happLocale = null;
                happUserId = null;
                happHwid = null;
                input.setPosition(legacyPosition);
                httpHeaders = input.readString();
                String s = input.readString();
                if (type == SubscriptionType.AGE) {
                    agePrivateKey = s;
                } else {
                    agePrivateKey = "";
                }
            }
        }
    }

    private static boolean looksSane(String value) {
        if (value == null) return true;
        if (value.length() > 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) return false;
        }
        return true;
    }

    public void deserializeFromShare(ByteBufferInput input) {
        int version = input.readInt();

        type = input.readInt();

        if (version < 6 && type == SubscriptionType.OOCv1) {
            input.readString(); // token, removed
            link = "";
        } else {
            link = input.readString();
        }
        if (version < 5) {
            input.readBoolean(); // forceResolve, removed
        }
        deduplication = input.readBoolean();
        if (version < 1) input.readBoolean();
        updateWhenConnectedOnly = input.readBoolean();
        customUserAgent = input.readString();

        if (type == SubscriptionType.RAW && version == 2) {
            input.readString(); // subscriptionUserinfo, removed
        }

        if (type != SubscriptionType.RAW || version >= 3) {
            bytesUsed = input.readLong();
            bytesRemaining = input.readLong();
        }

        if (version >= 3) {
            expiryDate = input.readLong();
        }

        if (version >= 4) {
            nameFilter = input.readString();
        }

        if (version < 6 && type == SubscriptionType.OOCv1) {
            input.readString();
            if (version <= 2) {
                input.readInt();
            }
            KryosKt.readStringList(input);
        }

        if (version >= 7) {
            nameFilter1 = input.readString();
        }

        if (version >= 8) {
            String s = input.readString();
            if (type == SubscriptionType.RAW || type == SubscriptionType.AGE) {
                httpHeaders = s;
            } else {
                httpHeaders = "";
            }
            s = input.readString();
            if (type == SubscriptionType.AGE) {
                agePrivateKey = s;
            } else {
                agePrivateKey = "";
            }
        }
    }

    @Override
    public void initializeDefaultValues() {
        if (type == null) type = SubscriptionType.RAW;
        if (link == null) link = "";
        if (deduplication == null) deduplication = false;
        if (updateWhenConnectedOnly == null) updateWhenConnectedOnly = false;
        if (customUserAgent == null) customUserAgent = "";
        if (autoUpdate == null) autoUpdate = false;
        if (autoUpdateDelay == null) autoUpdateDelay = 1440;
        if (lastUpdated == null) lastUpdated = 0L;

        if (bytesUsed == null) bytesUsed = 0L;
        if (bytesRemaining == null) bytesRemaining = 0L;
        if (nameFilter == null) nameFilter = "";
        if (nameFilter1 == null) nameFilter1 = "";
        if (importRoutingRules == null) importRoutingRules = false;
        if (happSpoof == null) happSpoof = false;
        if (happAppVersion == null) happAppVersion = "3.21.1";
        if (happOs == null) happOs = "Android";
        if (happOsVersion == null) happOsVersion = Build.VERSION.RELEASE;
        if (happDeviceModel == null) happDeviceModel = Build.MODEL;
        if (happLocale == null) happLocale = "en";
        if (happUserId == null) happUserId = "";
        if (happHwid == null) happHwid = "";

        if (expiryDate == null) expiryDate = 0L;

        if (httpHeaders == null) httpHeaders = "";
        if (agePrivateKey == null) agePrivateKey = "";
    }

    public static final Creator<SubscriptionBean> CREATOR = new CREATOR<>() {
        @NonNull
        @Override
        public SubscriptionBean newInstance() {
            return new SubscriptionBean();
        }

        @Override
        public SubscriptionBean[] newArray(int size) {
            return new SubscriptionBean[size];
        }
    };

}
