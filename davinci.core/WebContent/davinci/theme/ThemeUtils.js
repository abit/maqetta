dojo.provide("davinci.theme.ThemeUtils");



davinci.theme.CloneTheme = function(name, version, selector, directory, originalTheme, renameFiles){
	
	var fileBase = originalTheme.file.parent;
	var themeRootPath = new davinci.model.Path(directory).removeLastSegments(0);
	
	var project = originalTheme.resource.getProject();
	
	var resource = davinci.resource.findResource(project, themeRootPath.toString());
	if(resource.libraryId)
		resource.createResource();
	davinci.resource.copy(fileBase, directory, true);
	var themeRoot = davinci.resource.findResource(project, directory);
	var fileName = originalTheme.file.getName();
	/* remove the copied theme */
	var sameName = (name==originalTheme.name);
	var themeFile = null;
	if(!sameName){
		var badTheme = davinci.resource.findResource(project, directory + "/" + fileName);
		badTheme.deleteResource();
	}
	
	
	
	var directoryPath = new davinci.model.Path(themeRoot.getPath());
	var lastSeg = directoryPath.lastSegment();
	/* create the .theme file */
	if(!sameName)
		themeFile = themeRoot.createResource(lastSeg + ".theme");
	else
		themeFile = davinci.resource.findResource(project, directory + "/" + fileName);
	
	var themeJson = {};
	themeJson['className'] = selector;
	themeJson['name']= name;
	themeJson['version'] =version || originalTheme['version'], 
	themeJson['specVersion'] =  originalTheme['specVersion'];
	themeJson['files'] = originalTheme['files'];
	themeJson['meta'] = originalTheme['meta'];
	themeJson['themeEditorHtmls'] = originalTheme['themeEditorHtmls'];
	
	
	
	var oldClass = originalTheme['className'];
	var toSave = {};
	/* re-write CSS Selectors */
	for(var i=0;i<themeJson['files'].length;i++){
		var fileUrl = directoryPath.append(themeJson['files'][i]);
		
		var resource = davinci.resource.findResource(project, fileUrl);
		if(!sameName && renameFiles && resource.getName().indexOf(oldClass) > -1){
			var newName = resource.getName().replace(oldClass, selector);
			resource.rename(newName);
			themeJson['files'][i] =newName;
		}
		
		var cssModel = davinci.model.Factory.getInstance().getModel({url:resource.getPath(),
		    		   includeImports : true,
					   loader:function(url){
						
							var r1=  davinci.resource.findResource(project, url);
							return r1.getText();
						}
		});
		var elements = cssModel.find({'elementType':'CSSSelector', 'cls':oldClass});
		for(var i=0;i<elements.length;i++){
			elements[i].cls = selector;
			var file = elements[i].getCSSFile();
			toSave[file.url] = file;
			
		}
	}
	
	themeFile.setContents("(" + dojo.toJson(themeJson)+")");
	
	for(var name in toSave){
		var file = toSave[name];
		file.save();
	}
	/* re-write metadata */
	
	for(var i=0;i<themeJson['meta'].length;i++){
		var fileUrl = directoryPath.append(themeJson['meta'][i]);
		var file = davinci.resource.findResource(project, fileUrl.toString());
		var contents = file.getText();
		var newContents = contents.replace(new RegExp(oldClass, "g"), selector);
		file.setContents(newContents);
	}
	
	/* rewrite theme editor HTML */
	for(var i=0;i<themeJson['themeEditorHtmls'].length;i++){
		var fileUrl = directoryPath.append(themeJson['themeEditorHtmls'][i]);
		var file = davinci.resource.findResource(project, fileUrl.toString());
		var contents = file.getText();
		var htmlFile = new davinci.html.HTMLFile(fileUrl);
		htmlFile.setText(contents,true);
		var element = htmlFile.find({'elementType':'HTMLElement', 'tag':'body'},true);
		element.setAttribute('class',selector);
		
		
		htmlFile.save();
	}
}

