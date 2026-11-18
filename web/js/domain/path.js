export function pathProfileData(result, name, config) {
  if (!Array.isArray(config?.spheres) || !Array.isArray(config?.texts?.spheres?.table)) return { name, labelCode:null, topSpheres:[], topSphere:'', experiment:'', percent:undefined };
  const topSpheres = (result?.topSpheres || []).slice(0, 3);
  const index = config.spheres.findIndex(sphere => sphere.id === result?.topSpheres?.[0]);
  const experiment = index < 0 ? '' : config.texts.spheres.table[index]?.['Вход и первые пробы'] || '';
  return { name, labelCode:result?.riasecCode?.[0] || null, topSpheres, topSphere:result?.topSpheres?.[0] || '', experiment, percent:result?.spheres?.find(sphere => sphere.id === result?.topSpheres?.[0])?.percent };
}
