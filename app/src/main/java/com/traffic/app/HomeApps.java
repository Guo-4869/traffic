package com.traffic.app;

import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Detect installed HOME handlers; never persist their usage or display labels. */
final class HomeApps {
    static Set<String> packages(Context c) {
        PackageManager pm=c.getPackageManager();
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        Set<String> result=new HashSet<>();
        for(ResolveInfo info:pm.queryIntentActivities(home,0))
            if(info.activityInfo!=null) result.add(info.activityInfo.packageName);
        ResolveInfo current=pm.resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
        if(current!=null && current.activityInfo!=null && !"android".equals(current.activityInfo.packageName))
            result.add(current.activityInfo.packageName);
        return result;
    }
    static void remove(Store.Snapshot s,Set<String> homes) {
        s.sessions.removeIf(item->homes.contains(item.pkg));
        s.transitions.removeIf(t->homes.contains(t.from()) || homes.contains(t.to()));
        s.labels.keySet().removeAll(homes);
        s.state.active.keySet().removeAll(homes);
        if(homes.contains(s.state.focus)) s.state.focus=null;
    }
    private HomeApps() {}
}
