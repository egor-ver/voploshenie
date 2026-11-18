const sphereNames = {
  business: 'Бизнес и управление',
  finance: 'Финансы',
  it: 'Информационные технологии',
  engineering: 'Инженерия',
  biology: 'Биология и медицина',
  telecom: 'Телекоммуникации',
  public_service: 'Государственная служба'
};

export const sphereName = id => sphereNames[id] || id || '';
