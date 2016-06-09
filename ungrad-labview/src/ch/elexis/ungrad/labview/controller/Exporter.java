package ch.elexis.ungrad.labview.controller;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.osgi.framework.Bundle;

import ch.elexis.core.data.util.PlatformHelper;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.ungrad.Resolver;
import ch.elexis.ungrad.labview.Preferences;
import ch.elexis.ungrad.labview.model.Bucket;
import ch.elexis.ungrad.labview.model.LabResultsRow;
import ch.elexis.ungrad.labview.model.Result;
import ch.rgw.io.FileTool;
import ch.rgw.tools.TimeTool;

public class Exporter {
	private Resolver resolver = new Resolver();
	private LabContentProvider lcp;
	Logger log = Logger.getLogger(getClass().getName());
	
	public Exporter(LabContentProvider lcp){
		this.lcp = lcp;
	}
	
	public boolean runInBrowser(){
		try {
			String output = makeHtml();
			File tmp = File.createTempFile("ungrad", ".html");
			tmp.deleteOnExit();
			FileTool.writeTextFile(tmp, output);
			Program proggie = Program.findProgram("html");
			if (proggie != null) {
				proggie.execute(tmp.getAbsolutePath());
			} else {
				if (Program.launch(tmp.getAbsolutePath()) == false) {
					Runtime.getRuntime().exec(tmp.getAbsolutePath());
				}
			}
			return true;
			
		} catch (Exception ex) {
			ex.printStackTrace();
			log.log(Level.SEVERE, "could not create HTML " + ex.getMessage());
			return false;
		}
	}
	
	public boolean createHTML(Composite parent){
		FileDialog fd = new FileDialog(parent.getShell(), SWT.SAVE);
		String file = fd.open();
		if (file != null) {
			try {
				String output = makeHtml();
				FileTool.writeTextFile(new File(file), output);
				return true;
			} catch (Exception e) {
				log.severe("Could not create HTML " + e.getMessage());
				return false;
			}
		}
		return false;
	}
	
	private String makeHtml() throws Exception{
		StringBuilder html = new StringBuilder("<table>");
		TimeTool[] dates = lcp.lrs.getDates();
		html.append(makeFullGrid(dates));
		html.append("</table>");
		String fallback = PlatformHelper.getBasePath("ch.elexis.ungrad.labview") + File.separator
			+ "doc" + File.separator + "laborblatt_beispiel.html";
		File tmpl = new File(Preferences.cfg.get(Preferences.TEMPLATE, fallback));
		if (!tmpl.exists() || !tmpl.canRead()) {
			SWTHelper.showError("Vorlage fehlt",
				"Die Vorlagendatei für den HTML Export wurde nicht gefunden. Bitte unter 'Einstellungen' nachprüfen");
		}
		String rawTemplate = FileTool.readTextFile(tmpl); // will throw Exception if not found
		String template = resolver.resolve(rawTemplate);
		String output = template.replace("[Laborwerte]", html.toString());
		return output;
		
	}
	
	private String makeFullGrid(TimeTool[] dates){
		StringBuilder ret = new StringBuilder("<table class=\"fullgrid\">");
		ret.append("<tr><th class=\"rowheader\">Parameter</th><th class=\"ref\">Referenz</th>");
		int lim = dates.length > 9 ? dates.length - 9 : -1;
		for (int i = dates.length - 1; i > lim; i--) {
			ret.append("<th>").append(dates[i].toString(TimeTool.DATE_GER)).append("</th>");
		}
		if (lim > -1) {
			ret.append("<th>früher</td>");
		}
		ret.append("</tr>");
		for (Object o : lcp.getElements(this)) {
			if (o.toString().startsWith("00") || o.toString().contains("?")) {
				continue;
			}
			ret.append("<tr><th class=\"group\" colspan=\""+dates.length+2+"\">").append(o.toString()).append("</th></tr>");
			for (Object l : lcp.getChildren(o)) {
				if (l instanceof LabResultsRow) {
					LabResultsRow lr = (LabResultsRow) l;
					ret.append("<tr>");
					ret.append("<td class=\"rowheader\">").append(lr.getItem().get("titel"))
						.append("</td><td class=\"ref\">").append(lr.getItem().get("refMann"))
						.append("</td>");
					for (int i = dates.length - 1; i > lim; i--) {
						Result res = lr.get(dates[i]);
						if (res == null) {
							ret.append("<td></td>");
						} else {
							ret.append("<td>").append(make(lr,res)).append("</td>");
						}
					}
					if (lim > -1) {
						Bucket bucket = lcp.lrs.getOlderBucket(lr.getItem());
						ret.append("<td>").append(bucket.getMinResult()).append(" - ")
							.append(bucket.getMaxResult()).append("</td>");
					}
					ret.append("</tr>");
				}
			}
			
		}
		return ret.toString();
	}
	private String make(LabResultsRow lr, Result res){
		String raw=res.get("resultat").replaceAll("\\s+", "");
		if(raw.startsWith("-")){
			return "-";
		}
		if(lr.getItem().isPathologic(lr.getPatient(), raw)){
			return "<span class=\"pathologic\">"+raw+"</span>";
		}
		return raw;
	}
}
