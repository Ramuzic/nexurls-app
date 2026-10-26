# Adds the NexWidget home screen widget (a WidgetKit app extension) to the iPhone project
# and embeds it in the app. Safe to run more than once: it stops if the target is there.
require 'xcodeproj'

path = File.expand_path('../ios/App/App.xcodeproj', __dir__)
proj = Xcodeproj::Project.open(path)
app  = proj.targets.find { |t| t.name == 'App' } or abort('App target not found')
if proj.targets.any? { |t| t.name == 'NexWidget' }
  puts 'NexWidget is already in the project'
  exit 0
end

widget = proj.new_target(:app_extension, 'NexWidget', :ios, '16.0', nil, :swift)

group = proj.main_group.find_subpath('NexWidget', true)
group.set_source_tree('<group>')
group.set_path('NexWidget')
swift = group.new_reference('NexWidget.swift')
group.new_reference('Info.plist')
widget.add_file_references([swift])
widget.add_system_framework(%w[WidgetKit SwiftUI])

app_release = app.build_configurations.find { |c| c.name == 'Release' }.build_settings
widget.build_configurations.each do |c|
  s = c.build_settings
  s['PRODUCT_BUNDLE_IDENTIFIER'] = 'com.nexurls.app.NexWidget'
  s['PRODUCT_NAME'] = '$(TARGET_NAME)'
  s['INFOPLIST_FILE'] = 'NexWidget/Info.plist'
  s['GENERATE_INFOPLIST_FILE'] = 'NO'
  s['SWIFT_VERSION'] = '5.0'
  s['TARGETED_DEVICE_FAMILY'] = '1'
  s['IPHONEOS_DEPLOYMENT_TARGET'] = '16.0'
  s['MARKETING_VERSION'] = app_release['MARKETING_VERSION'] || '1.1.1'
  s['CURRENT_PROJECT_VERSION'] = app_release['CURRENT_PROJECT_VERSION'] || '1'
  s['CODE_SIGN_STYLE'] = app_release['CODE_SIGN_STYLE'] || 'Automatic'
  s['SKIP_INSTALL'] = 'YES'
  s['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks']
  s['APPLICATION_EXTENSION_API_ONLY'] = 'YES'
end

embed = app.new_copy_files_build_phase('Embed Foundation Extensions')
embed.symbol_dst_subfolder_spec = :plug_ins
file = embed.add_file_reference(widget.product_reference, true)
file.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
app.add_dependency(widget)

proj.save
puts 'NexWidget added and embedded in App'
