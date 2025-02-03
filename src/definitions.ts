export interface StreamCallPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
